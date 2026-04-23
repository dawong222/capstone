package com.capstone.capstone.dto.mqtt;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MqttStationDto {
    private MqttHeaderDto header;
    private MqttStationPayloadDto payload;
    private MqttStatusDto status;
}
