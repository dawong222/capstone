package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StationScheduleDto {

    private Long stationId;
    private List<HourlyPlanDto> hourlyPlan;
}