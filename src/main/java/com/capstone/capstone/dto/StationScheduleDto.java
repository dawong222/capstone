package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StationScheduleDto {

    @JsonAlias("station_id")
    private Long stationId;

    @JsonAlias("hourly_plan")
    private List<HourlyPlanDto> hourlyPlan;
}