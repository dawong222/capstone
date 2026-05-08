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

    @JsonAlias({"ess_power", "ess_power_kw"})
    private double essPower;

    @JsonAlias({"grid_usage", "grid_usage_kw"})
    private double gridUsage;

    @JsonAlias({"pv_priority", "pv_priority_kw"})
    private double pvPriority;

    private List<TransferDto> transfer;
}