package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StationScheduleDto {

    @JsonProperty("station_id")
    private Long stationId;

    @JsonProperty("hourly_plan")
    private List<HourlyPlanDto> hourlyPlan;
}