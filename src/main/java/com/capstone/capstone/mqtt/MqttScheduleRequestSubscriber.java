package com.capstone.capstone.mqtt;

import com.capstone.capstone.service.AiScheduleService;
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
public class MqttScheduleRequestSubscriber {

    private final AiScheduleService aiScheduleService;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "mqttScheduleRequestChannel")
    public void handleMessage(String payload) {
        log.info("[MQTT 스케줄 요청 수신] payload 길이={}", payload.length());
        try {
            Map<String, Object> request = objectMapper.readValue(payload, new TypeReference<>() {});
            aiScheduleService.enrichAndSend(request);
        } catch (Exception e) {
            log.error("[MQTT 스케줄 요청 처리 실패] {}", e.getMessage());
        }
    }
}
