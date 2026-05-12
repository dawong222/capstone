package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttStateOfChargeDto {
    private String mode;
    private double soc;
    @JsonProperty("capacity_wh")
    private double capacityWh;
}
