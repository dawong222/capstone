package com.capstone.capstone.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Schema(description = "에너지 통계 DTO")
public class EnergySummaryDto {

    private float totalPowerUsage;
    private float totalCost;
    private float totalPvUsed;
    private float totalGridUsed;
    private LocalDateTime timestamp;
}