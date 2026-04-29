package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleResponseDto {
    private String requestId;
    private String targetDate;
    private String createdAt;
    private String status;
    private List<StationScheduleResponseDto> stations;
}
