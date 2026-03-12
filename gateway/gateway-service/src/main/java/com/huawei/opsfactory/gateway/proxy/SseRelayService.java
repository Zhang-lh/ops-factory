package com.huawei.opsfactory.gateway.proxy;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SseRelayService {

    private static final Logger log = LogManager.getLogger(SseRelayService.class);

    private final GoosedProxy goosedProxy;
    private final WebClient webClient;
    private final GatewayProperties properties;

    public SseRelayService(GoosedProxy goosedProxy, GatewayProperties properties) {
        this.goosedProxy = goosedProxy;
        this.webClient = goosedProxy.getWebClient();
        this.properties = properties;
    }

    /**
     * Relay SSE stream from a goosed instance.
     * Returns a Flux of raw DataBuffer chunks for zero-copy streaming.
     *
     * Three timeout layers protect against goosed hangs:
     * 1. firstByteTimeout — abort if no data arrives at all (prepare_reply_context hung)
     * 2. idleTimeout — abort if gap between chunks exceeds threshold (tool execution hung)
     * 3. maxDuration — hard ceiling on any single reply
     */
    public Flux<DataBuffer> relay(int port, String path, String body) {
        String target = goosedProxy.goosedBaseUrl(port) + path;
        long startTime = System.currentTimeMillis();
        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicLong lastChunkTime = new AtomicLong(startTime);
        AtomicLong totalBytes = new AtomicLong(0);

        GatewayProperties.Sse sseConfig = properties.getSse();
        Duration firstByteTimeout = Duration.ofSeconds(sseConfig.getFirstByteTimeoutSec());
        Duration idleTimeout = Duration.ofSeconds(sseConfig.getIdleTimeoutSec());
        Duration maxDuration = Duration.ofSeconds(sseConfig.getMaxDurationSec());

        log.info("[SSE-DIAG] relay START → {} body={}chars firstByte={}s idle={}s max={}s",
                target, body.length(),
                sseConfig.getFirstByteTimeoutSec(),
                sseConfig.getIdleTimeoutSec(),
                sseConfig.getMaxDurationSec());

        Flux<DataBuffer> upstream = webClient.post()
                .uri(target)
                .header(GatewayConstants.HEADER_SECRET_KEY, properties.getSecretKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnNext(buf -> {
                    int seq = chunkCount.incrementAndGet();
                    long now = System.currentTimeMillis();
                    long gap = now - lastChunkTime.getAndSet(now);
                    int readable = buf.readableByteCount();
                    totalBytes.addAndGet(readable);

                    // Log first 3 chunks always, then every 10th, and any chunk after >5s gap
                    if (seq <= 3 || seq % 10 == 0 || gap > 5000) {
                        String preview = peekContent(buf, 120);
                        log.info("[SSE-DIAG] chunk#{} {}B gap={}ms elapsed={}ms preview={}",
                                seq, readable, gap, now - startTime, preview);
                    }
                })
                .doOnError(e -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.error("[SSE-DIAG] relay ERROR after {}ms, chunks={}, bytes={}: {}",
                            elapsed, chunkCount.get(), totalBytes.get(), e.getMessage());
                })
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("[SSE-DIAG] relay COMPLETE {}ms chunks={} bytes={}",
                            elapsed, chunkCount.get(), totalBytes.get());
                })
                .doOnCancel(() -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    log.warn("[SSE-DIAG] relay CANCELLED after {}ms, chunks={}, bytes={}",
                            elapsed, chunkCount.get(), totalBytes.get());
                });

        // Layer 1+2: First-byte timeout and per-element idle timeout.
        // timeout(Publisher, Function) uses firstTimeout for the first element,
        // then the Function produces a new timeout publisher after each element.
        Flux<DataBuffer> withTimeouts = upstream
                .timeout(
                        Mono.delay(firstByteTimeout),
                        item -> Mono.delay(idleTimeout)
                )
                // Layer 3: Hard max duration
                .take(maxDuration);

        // On timeout, emit a synthetic SSE error event so the webapp can show a message
        return withTimeouts
                .onErrorResume(e -> {
                    if (e instanceof TimeoutException) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        int chunks = chunkCount.get();
                        String reason = chunks == 0
                                ? "No response from agent in " + sseConfig.getFirstByteTimeoutSec() + "s"
                                : "Agent stopped responding for " + sseConfig.getIdleTimeoutSec() + "s";
                        log.warn("[SSE-DIAG] relay TIMEOUT after {}ms, chunks={}, bytes={}: {}",
                                elapsed, chunks, totalBytes.get(), reason);
                        return sseErrorEvent(reason);
                    }
                    if (e instanceof WebClientRequestException) {
                        log.warn("[SSE-DIAG] relay CONNECTION ERROR: {}", e.getMessage());
                        return sseErrorEvent("Agent connection failed: " + e.getMessage());
                    }
                    // Other errors: propagate
                    return Flux.error(e);
                });
    }

    /**
     * Create a synthetic SSE error event that the webapp can parse and display.
     */
    private Flux<DataBuffer> sseErrorEvent(String reason) {
        String ssePayload = "data: {\"type\":\"Error\",\"error\":\"" +
                reason.replace("\"", "\\\"") + "\"}\n\n";
        DataBuffer buf = DefaultDataBufferFactory.sharedInstance
                .wrap(ssePayload.getBytes(StandardCharsets.UTF_8));
        return Flux.just(buf);
    }

    /**
     * Peek at the first N bytes of a DataBuffer without consuming it.
     */
    private static String peekContent(DataBuffer buf, int maxLen) {
        try {
            int readable = buf.readableByteCount();
            int len = Math.min(readable, maxLen);
            byte[] bytes = new byte[len];
            int pos = buf.readPosition();
            buf.read(bytes);
            buf.readPosition(pos); // reset position so downstream can still read
            String s = new String(bytes, StandardCharsets.UTF_8)
                    .replace("\n", "\\n").replace("\r", "\\r");
            return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
        } catch (Exception e) {
            return "<peek-error>";
        }
    }
}
