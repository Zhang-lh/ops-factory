package com.huawei.opsfactory.gateway.service;

import com.huawei.opsfactory.gateway.common.constants.GatewayConstants;
import com.huawei.opsfactory.gateway.common.model.ManagedInstance;
import com.huawei.opsfactory.gateway.process.InstanceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class SessionService {

    private static final Logger log = LogManager.getLogger(SessionService.class);

    private final InstanceManager instanceManager;
    private final com.huawei.opsfactory.gateway.proxy.GoosedProxy goosedProxy;
    private final WebClient webClient;

    public SessionService(InstanceManager instanceManager,
                          com.huawei.opsfactory.gateway.proxy.GoosedProxy goosedProxy) {
        this.instanceManager = instanceManager;
        this.goosedProxy = goosedProxy;
        this.webClient = goosedProxy.getWebClient();
    }

    /**
     * Query sessions from a specific goosed instance.
     */
    public Mono<String> getSessionsFromInstance(ManagedInstance instance) {
        String url = goosedProxy.goosedBaseUrl(instance.getPort()) + "/sessions";
        return webClient.get()
                .uri(url)
                .header(GatewayConstants.HEADER_SECRET_KEY, instance.getSecretKey())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorReturn("[]");
    }
}

