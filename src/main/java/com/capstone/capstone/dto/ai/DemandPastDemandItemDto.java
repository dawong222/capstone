package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DemandPastDemandItemDto {

    @JsonProperty("tm")
    private String tm;

    @JsonProperty("station_name")
    private String stationName;

    @JsonProperty("slot_start")
    private String slotStart;

    @JsonProperty("slot_end")
    private String slotEnd;

    @JsonProperty("demand_kwh")
    private double demandKwh;
}
