package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class HourlyPlanDto {

    private int hour;

    @JsonProperty("slot_label")
    private String slotLabel;

    @JsonProperty("slot_start")
    private String slotStart;

    @JsonProperty("slot_end")
    private String slotEnd;

    @JsonProperty("ess_mode")
    private String essMode;

    @JsonProperty("ess_power_kw")
    private Double essPowerKw;

    @JsonProperty("ess_power_signed_kw")
    private Double essPowerSignedKw;

    @JsonProperty("ess_energy_kwh")
    private Double essEnergyKwh;

    @JsonProperty("grid_usage_kw")
    private Double gridUsageKw;

    @JsonProperty("grid_usage_kwh")
    private Double gridUsageKwh;

    @JsonProperty("pv_generation_pred_kwh")
    private Double pvGenerationPredKwh;

    @JsonProperty("load_pred_kwh")
    private Double loadPredKwh;

    @JsonProperty("pv_priority")
    private Double pvPriority;

    @JsonProperty("expected_soc")
    private Double expectedSoc;

    private List<TransferDto> transfer;
}
