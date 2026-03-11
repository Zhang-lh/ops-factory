package com.huawei.opsfactory.gateway.controller;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.filter.UserContextFilter;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import com.huawei.opsfactory.gateway.proxy.GoosedProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/agents/{agentId}/mcp")
public class McpController {

    private static final Logger log = LogManager.getLogger(McpController.class);

    private final InstanceManager instanceManager;
    private final GoosedProxy goosedProxy;

    public McpController(InstanceManager instanceManager, GoosedProxy goosedProxy) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
    }

    @GetMapping
    public Mono<Void> getMcpExtensions(@PathVariable String agentId, ServerWebExchange exchange) {
        requireAdmin(exchange);
        // Route to sys instance
        return instanceManager.getOrSpawn(agentId, GatewayConstants.SYS_USER)
                .flatMap(instance -> goosedProxy.proxy(
                        exchange.getRequest(), exchange.getResponse(),
                        instance.getPort(), "/config/extensions"));
    }

    @PostMapping
    public Mono<String> createMcpExtension(@PathVariable String agentId,
                                            @RequestBody String body,
                                            ServerWebExchange exchange) {
        requireAdmin(exchange);

        // 1. Forward to sys instance first
        return instanceManager.getOrSpawn(agentId, GatewayConstants.SYS_USER)
                .flatMap(sysInstance -> {
                    WebClient wc = goosedProxy.getWebClient();
                    String sysTarget = goosedProxy.goosedBaseUrl(sysInstance.getPort());

                    return wc.post()
                            .uri(sysTarget + "/config/extensions")
                            .header(GatewayConstants.HEADER_SECRET_KEY, goosedProxy.getSecretKey())
                            .header("Content-Type", "application/json")
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(sysResult -> {
                                // 2. Fanout to all user instances
                                fanout(agentId, "/config/extensions", body);
                                return Mono.just(sysResult);
                            });
                });
    }

    @DeleteMapping("/{name}")
    public Mono<String> deleteMcpExtension(@PathVariable String agentId,
                                            @PathVariable String name,
                                            ServerWebExchange exchange) {
        requireAdmin(exchange);
        String path = "/config/extensions/" + name;

        return instanceManager.getOrSpawn(agentId, GatewayConstants.SYS_USER)
                .flatMap(sysInstance -> {
                    WebClient wc = goosedProxy.getWebClient();
                    String sysTarget = goosedProxy.goosedBaseUrl(sysInstance.getPort());

                    return wc.delete()
                            .uri(sysTarget + path)
                            .header(GatewayConstants.HEADER_SECRET_KEY, goosedProxy.getSecretKey())
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(sysResult -> {
                                // Fanout DELETE to user instances
                                fanout(agentId, path, null);
                                return Mono.just(sysResult);
                            });
                });
    }

    /**
     * Fire-and-forget fanout to all running user instances for this agent.
     * @param body if non-null, sends a POST with this body; otherwise sends a DELETE
     */
    private void fanout(String agentId, String path, String body) {
        WebClient wc = goosedProxy.getWebClient();
        String verb = body != null ? "POST" : "DELETE";
        Flux.fromIterable(instanceManager.getAllInstances())
                .filter(inst -> inst.getAgentId().equals(agentId)
                        && !GatewayConstants.SYS_USER.equals(inst.getUserId())
                        && inst.getStatus() == ManagedInstance.Status.RUNNING)
                .flatMap(inst -> {
                    String target = goosedProxy.goosedBaseUrl(inst.getPort()) + path;
                    WebClient.RequestHeadersSpec<?> spec;
                    if (body != null) {
                        spec = wc.post().uri(target)
                                .header(GatewayConstants.HEADER_SECRET_KEY, goosedProxy.getSecretKey())
                                .header("Content-Type", "application/json")
                                .bodyValue(body);
                    } else {
                        spec = wc.delete().uri(target)
                                .header(GatewayConstants.HEADER_SECRET_KEY, goosedProxy.getSecretKey());
                    }
                    return spec.retrieve()
                            .bodyToMono(String.class)
                            .onErrorResume(e -> {
                                log.warn("MCP fanout {} failed for {}:{}: {}",
                                        verb, agentId, inst.getUserId(), e.getMessage());
                                return Mono.empty();
                            });
                })
                .subscribe();
    }

    private void requireAdmin(ServerWebExchange exchange) {
        UserContextFilter.requireAdmin(exchange);
    }
}
