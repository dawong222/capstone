package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HourlyPlanDto {

    private int hour;
    private String essMode;
    private double essPower;
    private double gridUsage;
    private double pvPriority;

    private List<TransferDto> transfer;
}