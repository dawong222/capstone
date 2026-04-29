package com.capstone.capstone.controller;

import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.service.DataProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final DataProcessingService dataProcessingService;

    @GetMapping("/latest")
    public ResponseEntity<MqttPayloadDto> getLatest() {
        MqttPayloadDto data = dataProcessingService.getLatestData();
        if (data == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(data);
    }
}
