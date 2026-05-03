package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ScheduleForecastRequestDto {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("request_timestamp")
    private String requestTimestamp;

    @JsonProperty("schedule_target_date")
    private String scheduleTargetDate;

    @JsonProperty("schedule_horizon_hours")
    private int scheduleHorizonHours;

    @JsonProperty("target_window")
    private TargetWindowDto targetWindow;

    @JsonProperty("demand_past_demand_hourly")
    private List<DemandPastDemandItemDto> demandPastDemandHourly;

    @JsonProperty("demand_past_weather_hourly")
    private List<PastWeatherItemDto> demandPastWeatherHourly;

    @JsonProperty("demand_forecast_short_term_hourly")
    private List<ForecastWeatherItemDto> demandForecastShortTermHourly;

    @JsonProperty("pv_past_generation_hourly")
    private List<PvPastGenerationItemDto> pvPastGenerationHourly;

    @JsonProperty("pv_past_weather_hourly")
    private List<PastWeatherItemDto> pvPastWeatherHourly;

    @JsonProperty("pv_forecast_short_term_hourly")
    private List<ForecastWeatherItemDto> pvForecastShortTermHourly;

    @JsonProperty("station_current_states")
    private List<StationCurrentStateItemDto> stationCurrentStates;

    @JsonProperty("tou_price_hourly")
    private List<TouPriceItemDto> touPriceHourly;

    @JsonProperty("grid_constraints")
    private GridConstraintsDto gridConstraints;

    @JsonProperty("ess_constraints")
    private EssConstraintsDto essConstraints;

    @JsonProperty("transfer_topology")
    private TransferTopologyDto transferTopology;
}
