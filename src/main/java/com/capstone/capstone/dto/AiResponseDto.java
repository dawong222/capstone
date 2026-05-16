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

    @JsonProperty("schedule_target_date")
    private String scheduleTargetDate;

    @JsonProperty("schedule_horizon_hours")
    private Integer scheduleHorizonHours;

    @JsonProperty("schedule_mode")
    private String scheduleMode;

    private ModelDto model;
    private Status status;

    @JsonProperty("forecast_results")
    private ForecastResultsDto forecastResults;

    private MetricsDto metrics;

    @JsonProperty("station_day_ahead_schedule")
    private List<StationScheduleDto> stationDayAheadSchedule;

    @Getter @Setter
    public static class Status {
        @JsonProperty("is_success")
        private boolean success;

        @JsonProperty("error_code")
        private int errorCode;

        private String message;
    }

    @Getter @Setter
    public static class ModelDto {
        private String algorithm;
        private String version;

        @JsonProperty("model_path")
        private String modelPath;

        private String device;

        @JsonProperty("transfer_action_enabled")
        private Boolean transferActionEnabled;

        @JsonProperty("transfer_postprocess_enabled")
        private Boolean transferPostprocessEnabled;

        private String description;
    }

    @Getter @Setter
    public static class ForecastResultsDto {
        @JsonProperty("pv_day_ahead_forecast")
        private List<PvForecastSlotDto> pvDayAheadForecast;

        @JsonProperty("demand_day_ahead_forecast")
        private List<DemandForecastSlotDto> demandDayAheadForecast;
    }

    @Getter @Setter
    public static class PvForecastSlotDto {
        private Integer slot;
        private Integer hour;

        @JsonProperty("slot_label")
        private String slotLabel;

        @JsonProperty("slot_start")
        private String slotStart;

        @JsonProperty("slot_end")
        private String slotEnd;

        @JsonProperty("predicted_pv_kwh_per_station")
        private Double predictedPvKwhPerStation;

        @JsonProperty("predicted_cluster_pv_kwh")
        private Double predictedClusterPvKwh;
    }

    @Getter @Setter
    public static class DemandForecastSlotDto {
        @JsonProperty("station_id")
        private Integer stationId;

        @JsonProperty("station_name")
        private String stationName;

        private Integer slot;
        private Integer hour;

        @JsonProperty("slot_label")
        private String slotLabel;

        @JsonProperty("slot_start")
        private String slotStart;

        @JsonProperty("slot_end")
        private String slotEnd;

        @JsonProperty("predicted_demand_kwh")
        private Double predictedDemandKwh;
    }

    @Getter @Setter
    public static class MetricsDto {
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

        @JsonProperty("total_pv_kwh")
        private Double totalPvKwh;

        @JsonProperty("total_grid_usage_kwh")
        private Double totalGridUsageKwh;

        @JsonProperty("total_self_supply_kwh")
        private Double totalSelfSupplyKwh;

        @JsonProperty("total_pv_lost_kwh")
        private Double totalPvLostKwh;

        @JsonProperty("total_transfer_out_kwh")
        private Double totalTransferOutKwh;

        @JsonProperty("total_transfer_in_kwh")
        private Double totalTransferInKwh;

        @JsonProperty("total_transfer_loss_kwh")
        private Double totalTransferLossKwh;

        @JsonProperty("max_cluster_grid_usage_kwh_per_hour")
        private Double maxClusterGridUsageKwhPerHour;

        @JsonProperty("peak_violation_slots")
        private Integer peakViolationSlots;

        @JsonProperty("soc_min")
        private Double socMin;

        @JsonProperty("soc_max")
        private Double socMax;

        @JsonProperty("total_reward_before_transfer")
        private Double totalRewardBeforeTransfer;

        private String device;

        @JsonProperty("torch_cuda_available")
        private Boolean torchCudaAvailable;

        @JsonProperty("cuda_device_name")
        private String cudaDeviceName;

        @JsonProperty("transfer_postprocess_enabled")
        private Boolean transferPostprocessEnabled;
    }
}
