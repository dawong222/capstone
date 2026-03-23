package com.capstone.capstone.controller;

import com.capstone.capstone.dto.StationStatusDto;
import com.capstone.capstone.entity.StationStatus;
import com.capstone.capstone.repository.StationStatusRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "충전소 실시간 상태 조회 API")
public class MonitoringController {

    private final StationStatusRepository statusRepository;

    @Operation(summary = "실시간 충전소 상태 조회", description = "특정 충전소의 최신 상태 데이터를 조회합니다.")
    @GetMapping("/{stationId}")
    public StationStatusDto getCurrentStatus(
            @Parameter(description = "충전소 ID", example = "1")
            @PathVariable Long stationId) {

        StationStatus status = statusRepository
                .findTopByStationIdOrderByUpdatedAtDesc(stationId)
                .orElseThrow();

        return new StationStatusDto(
                status.getCurrentPowerUsage(),
                status.getCurrentEssSoc(),
                status.getCurrentPvGeneration(),
                status.getCurrentEvDemand(),
                status.getActiveEvCount(),
                status.getUpdatedAt()
        );
    }
}