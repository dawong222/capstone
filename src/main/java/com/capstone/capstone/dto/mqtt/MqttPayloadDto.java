package com.capstone.capstone.dto.mqtt;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class MqttPayloadDto {
    private List<MqttStationDto> stations;
}
