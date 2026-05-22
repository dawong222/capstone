package com.capstone.capstone.controller;

import com.capstone.capstone.mqtt.MqttPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/control")
@RequiredArgsConstructor
public class ControlController {

    private final MqttPublisher mqttPublisher;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.publish-topic}")
    private String publishTopic;

    /**
     * IoT 제어 명령을 MQTT 브로커의 /control/station 토픽으로 발행한다.
     * type: "emergency_stop" | "restart"
     */
    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> sendCommand(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        Object stationId = body.get("stationId");

        if (type == null || stationId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "type, stationId 필드가 필요합니다."));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("station_id", stationId);
        payload.put("timestamp", LocalDateTime.now().toString());

        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttPublisher.publish(publishTopic, json);
            log.info("[제어 명령 발행] topic={}, type={}, station_id={}", publishTopic, type, stationId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("topic", publishTopic);
            result.put("payload", payload);
            result.put("message", "제어 명령이 MQTT로 전송되었습니다.");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[제어 명령 발행 실패] {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
