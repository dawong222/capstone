package com.capstone.capstone.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "AI 스케줄링 결과 정보")
@Getter
@AllArgsConstructor
public class SchedulingResultDto {

    @Schema(description = "ESS 충/방전 제어 값", example = "10.5")
    private float essAction;

    @Schema(description = "태양광 사용량 제어 값", example = "5.2")
    private float pvAction;

    @Schema(description = "계통 전력 사용량", example = "20.0")
    private float gridUsage;

    @Schema(description = "자가 소비 전력량", example = "15.0")
    private float selfConsumption;

    @Schema(description = "총 비용", example = "5000")
    private float cost;

    @Schema(description = "강화학습 보상값", example = "0.85")
    private float reward;

    @Schema(description = "결과 생성 시간")
    private LocalDateTime timestamp;
}