package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttHeaderDto {
    @JsonProperty("station_id")
    private int stationId;
    @JsonProperty("is_physical")
    private boolean physical;  // Lombok: isPhysical()
    private String timestamp;
    @JsonProperty("day_idx")
    private int dayIdx;
    private int step;
}
