package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferDto {

    @JsonProperty("target_station_id")
    private Long targetStationId;

    private double power;
}