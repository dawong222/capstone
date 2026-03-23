package com.capstone.capstone.controller;

import com.capstone.capstone.dto.StationDto;
import com.capstone.capstone.entity.ChargingStation;
import com.capstone.capstone.repository.ChargingStationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stations")
@RequiredArgsConstructor
@Tag(name = "Station", description = "충전소 정보 API")
public class StationController {

    private final ChargingStationRepository stationRepository;

    // 🔹 전체 충전소 조회
    @Operation(summary = "충전소 목록 조회", description = "전체 충전소 리스트를 조회합니다.")
    @GetMapping
    public List<StationDto> getAllStations() {

        return stationRepository.findAll()
                .stream()
                .map(s -> new StationDto(
                        s.getId(),
                        s.getName(),
                        s.getLocation(),
                        s.getCreatedAt()
                ))
                .toList();
    }

    // 🔹 단일 충전소 조회
    @Operation(summary = "충전소 상세 조회", description = "특정 충전소 정보를 조회합니다.")
    @GetMapping("/{id}")
    public StationDto getStation(
            @Parameter(description = "충전소 ID", example = "1")
            @PathVariable Long id) {

        ChargingStation s = stationRepository.findById(id).orElseThrow();

        return new StationDto(
                s.getId(),
                s.getName(),
                s.getLocation(),
                s.getCreatedAt()
        );
    }
}