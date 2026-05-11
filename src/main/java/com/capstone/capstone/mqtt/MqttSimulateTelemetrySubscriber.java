package com.capstone.capstone.mqtt;

import com.capstone.capstone.service.AiRequestBuilderService;
import com.capstone.capstone.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttSimulateTelemetrySubscriber {

    private final AiRequestBuilderService aiRequestBuilderService;
    private final AiService aiService;

    @ServiceActivator(inputChannel = "mqttSimulateTelemetryChannel")
    public void handleMessage(String payload) {
        log.info("[simulate/telemetry 수신] AI 스케줄 요청 전송 시작");
        try {
            Map<String, Object> request = aiRequestBuilderService.buildRawAiRequest();
            aiService.sendRaw(request);
            log.info("[simulate/telemetry] AI 서버 전송 완료");
        } catch (Exception e) {
            log.error("[simulate/telemetry] AI 요청 실패: {}", e.getMessage());
        }
    }
}
