package com.iot.ruleengine.config;

import com.iot.ruleengine.websocket.DeviceStatusWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketHandlerConfig implements WebSocketConfigurer {

    private final DeviceStatusWebSocketHandler deviceStatusWebSocketHandler;

    @Autowired
    public WebSocketHandlerConfig(DeviceStatusWebSocketHandler deviceStatusWebSocketHandler) {
        this.deviceStatusWebSocketHandler = deviceStatusWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(deviceStatusWebSocketHandler, "/ws/device/status")
                .setAllowedOriginPatterns("*");
    }
}
