package com.capstone.capstone.controller;

import com.capstone.capstone.dto.SchedulingResultDto;
import com.capstone.capstone.entity.SchedulingResult;
import com.capstone.capstone.repository.SchedulingResultRepository;
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
@RequestMapping("/scheduling")
@RequiredArgsConstructor
@Tag(name = "Scheduling", description = "AI 기반 충전 스케줄링 결과 API")
public class SchedulingController {

    private final SchedulingResultRepository schedulingRepository;

    @Operation(summary = "스케줄링 결과 조회", description = "충전소별 AI 스케줄링 결과를 조회합니다.")
    @GetMapping("/{stationId}")
    public List<SchedulingResultDto> getResults(
            @Parameter(description = "충전소 ID", example = "1")
            @PathVariable Long stationId) {

        return schedulingRepository.findByStationId(stationId)
                .stream()
                .map(s -> new SchedulingResultDto(
                        s.getEssAction(),
                        s.getPvAction(),
                        s.getGridUsage(),
                        s.getSelfConsumption(),
                        s.getCost(),
                        s.getReward(),
                        s.getTimestamp()
                ))
                .toList();
    }
}