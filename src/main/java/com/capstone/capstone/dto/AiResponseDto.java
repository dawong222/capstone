package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AiResponseDto {

    private String requestId;
    private String timestamp;
    private Status status;

    private List<StationScheduleDto> stationDayAheadSchedule;

    @Getter @Setter
    public static class Status {
        private boolean isSuccess;
        private int errorCode;
        private String message;
    }
}