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
        private boolean success;  // Lombok: isSuccess()

        @JsonProperty("error_code")
        private int errorCode;

        private String message;
    }

    @Getter @Setter
    public static class Metrics {
        @JsonProperty("grid_only_cost_krw")
        private Double gridOnlyCostKrw;

        @JsonProperty("sac_cost_with_transfer_krw")
        private Double sacCostWithTransferKrw;

        @JsonProperty("cost_reduction_krw")
        private Double costReductionKrw;

        @JsonProperty("cost_reduction_pct")
        private Double costReductionPct;

        @JsonProperty("total_demand_kwh")
        private Double totalDemandKwh;

        @JsonProperty("total_grid_usage_kwh")
        private Double totalGridUsageKwh;
    }

    private Metrics metrics;
}