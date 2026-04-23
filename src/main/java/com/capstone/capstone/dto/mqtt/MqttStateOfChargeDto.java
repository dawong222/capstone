package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MqttStateOfChargeDto {
    private String mode;
    private double soc;
    @JsonProperty("capacity_wh")
    private double capacityWh;
}
