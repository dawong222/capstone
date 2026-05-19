package com.capstone.capstone.controller;

import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.entity.StationState;
import com.capstone.capstone.repository.StationStateRepository;
import com.capstone.capstone.service.DataProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final DataProcessingService dataProcessingService;
    private final StationStateRepository stationStateRepository;

    @GetMapping("/latest")
    public ResponseEntity<MqttPayloadDto> getLatest() {
        MqttPayloadDto data = dataProcessingService.getLatestData();
        if (data == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(data);
    }

    @GetMapping("/db-status")
    public ResponseEntity<List<Map<String, Object>>> getDbStatus() {
        List<StationState> states = stationStateRepository.findAll();
        List<Map<String, Object>> result = states.stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stationIndex", s.getStation() != null ? s.getStation().getStationIndex() : null);
            row.put("stationName", s.getStation() != null ? s.getStation().getName() : null);
            row.put("soc", s.getSoc());
            row.put("demandCount", s.getDemandCount());
            row.put("updatedAt", s.getUpdatedAt());
            return row;
        }).toList();
        return ResponseEntity.ok(result);
    }
}
