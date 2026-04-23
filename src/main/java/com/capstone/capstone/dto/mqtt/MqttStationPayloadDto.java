package com.capstone.capstone.dto.mqtt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class MqttStationPayloadDto {
    @JsonProperty("charger_status")
    private List<MqttChargerStatusDto> chargerStatus;
    @JsonProperty("power_metrics_w")
    private MqttPowerMetricsDto powerMetricsW;
    @JsonProperty("state_of_charge")
    private MqttStateOfChargeDto stateOfCharge;
}
