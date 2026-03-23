package com.capstone.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CostAnalysisDto {

    private float costWithoutAi;
    private float costWithAi;
    private float savings;
    private LocalDateTime timestamp;
}