package com.capstone.capstone.mqtt;

import com.capstone.capstone.service.AiService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttAiRequestSubscriber {

    private final AiService aiService;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "mqttAiRequestChannel")
    public void handleMessage(String payload) {
        log.info("[MQTT AI 요청 수신] payload 길이={}", payload.length());
        try {
            Map<String, Object> request = objectMapper.readValue(payload, new TypeReference<>() {});
            aiService.sendRaw(request);
        } catch (Exception e) {
            log.error("[MQTT AI 요청 처리 실패] {}", e.getMessage());
        }
    }
}
