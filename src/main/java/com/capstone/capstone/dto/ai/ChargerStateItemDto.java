package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChargerStateItemDto {

    @JsonProperty("charger_id")
    private String chargerId;

    @JsonProperty("charger_type")
    private String chargerType;

    @JsonProperty("rated_power_kw")
    private double ratedPowerKw;

    @JsonProperty("is_active")
    private boolean active;

    @JsonProperty("current_power_kw")
    private double currentPowerKw;
}
