package com.huawei.opsfactory.gateway.proxy;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.config.GatewayProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

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
     */
    public Flux<DataBuffer> relay(int port, String path, String body) {
        String target = goosedProxy.goosedBaseUrl(port) + path;

        return webClient.post()
                .uri(target)
                .header(GatewayConstants.HEADER_SECRET_KEY, properties.getSecretKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnError(e -> log.error("SSE relay error for {}: {}", target, e.getMessage()));
    }
}
