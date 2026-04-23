package com.capstone.capstone.mqtt;

import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.service.DataProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttSubscriber {

    private final DataProcessingService service;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(String payload) {
        log.info("[MQTT RAW] {}", payload);
        MqttPayloadDto parsed = service.process(payload);
        log.info("[MQTT 파싱 완료] 스테이션 {}개", parsed.getStations() != null ? parsed.getStations().size() : 0);
    }
}
