package com.capstone.capstone.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Schema(description = "충전소 기본 정보")
public class StationDto {

    @Schema(description = "충전소 ID", example = "1")
    private Long id;

    @Schema(description = "충전소 이름", example = "강남 충전소")
    private String name;

    @Schema(description = "위치", example = "서울 강남구")
    private String location;

    @Schema(description = "생성 시간")
    private LocalDateTime createdAt;
}