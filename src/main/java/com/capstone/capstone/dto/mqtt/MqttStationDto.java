package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MqttStationDto {
    private MqttHeaderDto header;
    private MqttStationPayloadDto payload;
    private MqttStatusDto status;
}
