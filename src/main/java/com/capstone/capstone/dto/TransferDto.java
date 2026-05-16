package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferDto {

    @JsonProperty("target_station_id")
    private Long targetStationId;

    @JsonProperty("transfer_power_kw")
    private Double transferPowerKw;

    @JsonProperty("received_power_kw")
    private Double receivedPowerKw;

    @JsonProperty("loss_power_kw")
    private Double lossPowerKw;

    @JsonProperty("transfer_energy_kwh")
    private Double transferEnergyKwh;

    @JsonProperty("received_energy_kwh")
    private Double receivedEnergyKwh;

    @JsonProperty("loss_energy_kwh")
    private Double lossEnergyKwh;
}
