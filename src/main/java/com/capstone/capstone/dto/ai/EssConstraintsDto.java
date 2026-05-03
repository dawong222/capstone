package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EssConstraintsDto {

    @JsonProperty("ess_capacity_kwh_per_station")
    private double essCapacityKwhPerStation;

    @JsonProperty("ess_min_soc")
    private double essMinSoc;

    @JsonProperty("ess_max_soc")
    private double essMaxSoc;

    @JsonProperty("ess_max_charge_kw")
    private double essMaxChargeKw;

    @JsonProperty("ess_max_discharge_kw")
    private double essMaxDischargeKw;

    @JsonProperty("round_trip_efficiency")
    private double roundTripEfficiency;
}
