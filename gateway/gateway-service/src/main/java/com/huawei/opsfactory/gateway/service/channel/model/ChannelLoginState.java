package com.huawei.opsfactory.gateway.service.channel.model;

public record ChannelLoginState(
        String channelId,
        String status,
        String message,
        String authStateDir,
        String sessionLabel,
        String selfPhone,
        String lastConnectedAt,
        String lastDisconnectedAt,
        String lastError,
        String qrCodeDataUrl
) {
}
