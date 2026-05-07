package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AiResponseDto {

    @JsonProperty("request_id")
    private String requestId;

    private String timestamp;
    private Status status;

    @JsonProperty("station_day_ahead_schedule")
    private List<StationScheduleDto> stationDayAheadSchedule;

    @Getter @Setter
    public static class Status {
        @JsonProperty("is_success")
        private boolean isSuccess;

        @JsonProperty("error_code")
        private int errorCode;

        private String message;
    }
}