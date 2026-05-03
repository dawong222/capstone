package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GridConstraintsDto {

    @JsonProperty("cluster_grid_limit_kw")
    private double clusterGridLimitKw;

    @JsonProperty("station_grid_limit_kw")
    private double stationGridLimitKw;

    @JsonProperty("peak_limit_kw")
    private double peakLimitKw;
}
