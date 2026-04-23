package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MqttPowerMetricsDto {
    @JsonProperty("p_pv")
    private double pPv;
    @JsonProperty("p_load")
    private double pLoad;
    @JsonProperty("p_ess")
    private double pEss;
    @JsonProperty("p_grid")
    private double pGrid;
    @JsonProperty("p_tr")
    private double pTr;
}
