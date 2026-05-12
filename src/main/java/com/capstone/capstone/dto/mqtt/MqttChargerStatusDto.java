package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttChargerStatusDto {
    @JsonProperty("charger_id")
    private int chargerId;
    @JsonProperty("has_demand")
    private boolean hasDemand;
}
