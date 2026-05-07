package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AiResponseDto {

    @JsonAlias("request_id")
    private String requestId;

    private String timestamp;

    private Status status;

    @JsonAlias("station_day_ahead_schedule")
    private List<StationScheduleDto> stationDayAheadSchedule;

    @Getter @Setter
    public static class Status {
        @JsonAlias("is_success")
        private boolean isSuccess;

        @JsonAlias("error_code")
        private int errorCode;

        private String message;
    }
}