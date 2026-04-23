package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MqttStatusDto {
    @JsonProperty("is_active")
    private boolean isActive;
    @JsonProperty("error_code")
    private int errorCode;
}
