package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferDto {

    @JsonAlias("target_station_id")
    private Long targetStationId;

    @JsonAlias({"power", "transfer_power_kw"})
    private double power;
}