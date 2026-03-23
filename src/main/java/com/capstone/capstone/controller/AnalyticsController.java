package com.capstone.capstone.controller;

import com.capstone.capstone.dto.CostAnalysisDto;
import com.capstone.capstone.dto.EnergySummaryDto;
import com.capstone.capstone.repository.CostAnalysisRepository;
import com.capstone.capstone.repository.EnergySummaryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "통계 및 비용 분석 API")
public class AnalyticsController {

    private final EnergySummaryRepository energyRepository;
    private final CostAnalysisRepository costRepository;

    @Operation(summary = "에너지 통계 조회", description = "충전소별 전력 사용량 및 비용 통계 조회")
    @GetMapping("/energy/{stationId}")
    public List<EnergySummaryDto> getEnergySummary(@PathVariable Long stationId) {
        return energyRepository.findByStationId(stationId)
                .stream()
                .map(e -> new EnergySummaryDto(
                        e.getTotalPowerUsage(),
                        e.getTotalCost(),
                        e.getTotalPvUsed(),
                        e.getTotalGridUsed(),
                        e.getTimestamp()
                ))
                .toList();
    }

    @Operation(summary = "비용 분석 조회", description = "AI 적용 전후 비용 비교")
    @GetMapping("/cost/{stationId}")
    public List<CostAnalysisDto> getCostAnalysis(@PathVariable Long stationId) {
        return costRepository.findByStationId(stationId)
                .stream()
                .map(c -> new CostAnalysisDto(
                        c.getCostWithoutAi(),
                        c.getCostWithAi(),
                        c.getSavings(),
                        c.getTimestamp()
                ))
                .toList();
    }
}