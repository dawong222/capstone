package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HourlyPlanDto {

    private int hour;

    @JsonProperty("ess_mode")
    private String essMode;

    @JsonProperty("ess_power")
    private double essPower;

    @JsonProperty("grid_usage")
    private double gridUsage;

    @JsonProperty("pv_priority")
    private double pvPriority;

    private List<TransferDto> transfer;
}