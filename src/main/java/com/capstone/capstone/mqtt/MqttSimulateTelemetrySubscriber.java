package com.capstone.capstone.mqtt;

import com.capstone.capstone.service.DemoScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Component;

/**
 * 데모 전용 MQTT 구독자.
 * simulate/telemetry 토픽으로 수신된 완성된 AI 요청을 DemoScheduleService로 위임한다.
 * DB 저장 없음 — 일반 모드(MqttSubscriber + DataProcessingService)와 완전히 분리된 경로.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttSimulateTelemetrySubscriber {

    private final DemoScheduleService demoScheduleService;

    @ServiceActivator(inputChannel = "mqttSimulateTelemetryChannel")
    public void handleMessage(String payload) {
        log.info("[DEMO MQTT] simulate/telemetry 수신 payload={}자", payload.length());
        demoScheduleService.handleSimulateTelemetry(payload);
    }
}
