package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class StationScheduleResponseDto {
    private Long stationId;
    private String stationName;
    private List<HourlyPlanDto> hourlyPlan;
}
