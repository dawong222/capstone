package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttStatusDto {
    @JsonProperty("is_active")
    private boolean active;  // Lombok: isActive() — Jackson은 isActive() 를 "active"로 해석하므로 필드명도 active로 맞춤
    @JsonProperty("error_code")
    private int errorCode;
}
