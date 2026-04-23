package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MqttChargerStatusDto {
    @JsonProperty("charger_id")
    private int chargerId;
    @JsonProperty("has_demand")
    private boolean hasDemand;
}
