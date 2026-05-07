package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HourlyPlanDto {

    private int hour;

    @JsonAlias("ess_mode")
    private String essMode;

    @JsonAlias("ess_power")
    private double essPower;

    @JsonAlias("grid_usage")
    private double gridUsage;

    @JsonAlias("pv_priority")
    private double pvPriority;

    private List<TransferDto> transfer;
}