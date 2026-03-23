package com.capstone.capstone.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "충전소 실시간 상태 정보")
@Getter
@AllArgsConstructor
public class StationStatusDto {

    @Schema(description = "현재 전력 사용량", example = "120.5")
    private float currentPowerUsage;

    @Schema(description = "ESS 현재 상태(SoC)", example = "75.0")
    private float currentEssSoc;

    @Schema(description = "현재 태양광 발전량", example = "30.2")
    private float currentPvGeneration;

    @Schema(description = "현재 충전 수요", example = "45.0")
    private float currentEvDemand;

    @Schema(description = "활성 충전 차량 수", example = "3")
    private int activeEvCount;

    @Schema(description = "데이터 업데이트 시간")
    private LocalDateTime updatedAt;
}