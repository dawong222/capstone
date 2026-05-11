package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttPayloadDto {
    private List<MqttStationDto> stations;
}
