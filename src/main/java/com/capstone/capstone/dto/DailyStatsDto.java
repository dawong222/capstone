package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DailyStatsDto {
    private String date;
    private double solar;       // kWh
    private double consumption; // kWh (pLoad)
    private double grid;        // kWh
    private double avgSoc;      // % (0~100)
    private int peakDemand;     // 최대 동시 충전 요청
}
