package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StationCurrentStateItemDto {

    @JsonProperty("station_id")
    private int stationId;

    @JsonProperty("station_name")
    private String stationName;

    @JsonProperty("is_physical")
    private boolean physical;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("ess_soc")
    private double essSoc;

    @JsonProperty("charger_count")
    private int chargerCount;

    @JsonProperty("chargers")
    private List<ChargerStateItemDto> chargers;

    @JsonProperty("error_code")
    private int errorCode;
}
