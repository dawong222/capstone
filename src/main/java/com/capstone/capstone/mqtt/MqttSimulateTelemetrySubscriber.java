package com.capstone.capstone.mqtt;

import com.capstone.capstone.service.AiService;
import com.capstone.capstone.service.DataProcessingService;
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
public class MqttSimulateTelemetrySubscriber {

    private final AiService aiService;
    private final DataProcessingService dataProcessingService;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "mqttSimulateTelemetryChannel")
    public void handleMessage(String payload) {
        log.info("[simulate/telemetry 수신] payload 길이={}", payload.length());
        try {
            Map<String, Object> request = objectMapper.readValue(payload, new TypeReference<>() {});
            aiService.sendRaw(request);
            log.info("[simulate/telemetry] AI 서버 전송 완료");
        } catch (Exception e) {
            log.error("[simulate/telemetry] AI 요청 실패: {}", e.getMessage());
        }

        // SSE로 프론트엔드에 실시간 텔레메트리 전달
        try {
            dataProcessingService.process(payload);
        } catch (Exception e) {
            log.warn("[simulate/telemetry] SSE 릴레이 실패 (무시): {}", e.getMessage());
        }
    }
}
