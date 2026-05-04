package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.dto.ai.*;
import com.capstone.capstone.dto.mqtt.MqttChargerStatusDto;
import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.dto.mqtt.MqttStationDto;
import com.capstone.capstone.entity.ChargingStation;
import com.capstone.capstone.entity.HourlySnapshot;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.HourlySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRequestBuilderService {

    private final DataProcessingService dataProcessingService;
    private final WeatherApiService weatherApiService;
    private final ChargingStationRepository stationRepository;
    private final HourlySnapshotRepository snapshotRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // ─── 실제 데이터 기반 요청 ────────────────────────────────────────

    public AiRequestDto buildAiRequest() {
        MqttPayloadDto latest = dataProcessingService.getLatestData();
        if (latest == null || latest.getStations() == null || latest.getStations().isEmpty()) {
            throw new IllegalStateException("No telemetry data available to build AiRequest");
        }
        AiRequestDto request = new AiRequestDto();
        request.setRequestId("rl-" + Instant.now().toEpochMilli());
        request.setRequestTimestamp(Instant.now().toString());
        request.setScheduleTargetDate(LocalDate.now().plusDays(1).toString());
        request.setScheduleHorizonHours("24");
        request.setClusterState(buildClusterState());
        request.setStations(buildStations(latest));
        Map<String, Object> weather = new HashMap<>();
        weather.put("historyRaw", weatherApiService.getHistoryRaw());
        // weather.put("forecastRaw", weatherApiService.fetchRawForecastItems(...));
        request.setWeather(weather);
        return request;
    }

    public ScheduleForecastRequestDto buildScheduleForecastRequest() {
        LocalDate d = LocalDate.now();
        LocalDate targetDate = d.plusDays(1);
        ZonedDateTime callTime = ZonedDateTime.now(KST);
        DateTimeFormatter iso     = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HHmmss");

        ScheduleForecastRequestDto req = new ScheduleForecastRequestDto();
        req.setRequestId(String.format("schedule-forecast-%s-%s-0001",
                d.format(dateFmt), callTime.format(timeFmt)));
        req.setRequestTimestamp(callTime.format(iso));
        req.setScheduleTargetDate(targetDate.toString());
        req.setScheduleHorizonHours(24);

        TargetWindowDto window = new TargetWindowDto();
        window.setStart(targetDate.atStartOfDay(KST).format(iso));
        window.setEnd(targetDate.plusDays(1).atStartOfDay(KST).format(iso));
        window.setSlotUnit("hour");
        window.setSlotCount(24);
        window.setSlotDefinition("slot 0 = 00:00~01:00, slot 23 = 23:00~24:00");
        req.setTargetWindow(window);

        List<ChargingStation> allStations = stationRepository.findAll();
        allStations.sort(Comparator.comparing(ChargingStation::getId));
        Map<Integer, String> stationNameMap = new HashMap<>();
        for (int i = 0; i < allStations.size(); i++) stationNameMap.put(i, allStations.get(i).getName());

        LocalDateTime rangeStart = d.minusDays(7).atStartOfDay();
        LocalDateTime rangeEnd   = d.atTime(21, 0, 0);
        List<HourlySnapshot> snapshots =
                snapshotRepository.findByRecordedAtBetweenOrderByRecordedAt(rangeStart, rangeEnd);

        String startDt     = d.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE);
        String endDt       = d.format(DateTimeFormatter.BASIC_ISO_DATE);
        String baseDateFmt = d.format(DateTimeFormatter.BASIC_ISO_DATE);

        req.setDemandPastDemandHourly(buildDemandPastDemand(snapshots, stationNameMap, iso));
        req.setDemandPastWeatherHourly(weatherApiService.fetchAsosWeather("108", startDt, endDt, iso));
        req.setDemandForecastShortTermHourly(weatherApiService.fetchVilageFcst(61, 125, baseDateFmt, targetDate, iso));
        req.setPvPastGenerationHourly(buildPvPastGeneration(snapshots, iso));
        req.setPvPastWeatherHourly(weatherApiService.fetchAsosWeather("108", startDt, endDt, iso));
        req.setPvForecastShortTermHourly(weatherApiService.fetchVilageFcst(61, 125, baseDateFmt, targetDate, iso));
        req.setStationCurrentStates(buildStationCurrentStates(allStations, callTime, iso));
        req.setTouPriceHourly(buildTouPriceHourly(targetDate, iso));
        req.setGridConstraints(buildGridConstraints());
        req.setEssConstraints(buildEssConstraints());
        req.setTransferTopology(buildTransferTopology(allStations));

        return req;
    }

    public Map<String, Object> buildRawAiRequest() {
        LocalDate d = LocalDate.now();
        LocalDate targetDate = d.plusDays(1);
        ZonedDateTime callTime = ZonedDateTime.now(KST);
        DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String startDt  = d.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE);
        String endDt    = d.format(DateTimeFormatter.BASIC_ISO_DATE);
        String baseDate = endDt;

        List<ChargingStation> allStations = stationRepository.findAll();
        allStations.sort(Comparator.comparing(ChargingStation::getId));
        Map<Integer, String> nameMap = new HashMap<>();
        for (int i = 0; i < allStations.size(); i++) nameMap.put(i, allStations.get(i).getName());

        List<HourlySnapshot> snapshots = snapshotRepository.findByRecordedAtBetweenOrderByRecordedAt(
                d.minusDays(7).atStartOfDay(), d.atTime(21, 0, 0));

        Map<String, Object> req = new LinkedHashMap<>();

        req.put("request_id", String.format("schedule-forecast-%s-%s-0001",
                d.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                callTime.format(DateTimeFormatter.ofPattern("HHmmss"))));
        req.put("request_timestamp", callTime.format(iso));
        req.put("schedule_target_date", targetDate.toString());
        req.put("schedule_horizon_hours", 24);

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("start", targetDate.atStartOfDay(KST).format(iso));
        window.put("end",   targetDate.plusDays(1).atStartOfDay(KST).format(iso));
        window.put("slot_unit",  "hour");
        window.put("slot_count", 24);
        window.put("slot_definition", "slot 0 = 00:00~01:00, slot 23 = 23:00~24:00");
        req.put("target_window", window);

        List<Map<String, Object>> demandPast = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            ZonedDateTime st = s.getRecordedAt().atZone(KST);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tm",           st.format(iso));
            item.put("station_name", nameMap.getOrDefault(s.getStationId(), "station-" + s.getStationId()));
            item.put("slot_start",   st.format(iso));
            item.put("slot_end",     st.plusHours(1).format(iso));
            item.put("demand_kwh",   s.getPLoad() != null ? s.getPLoad() / 1000.0 : 0.0);
            demandPast.add(item);
        }
        req.put("demand_past_demand_hourly", demandPast);
        req.put("demand_past_weather_hourly", weatherApiService.fetchRawAsosItems("108", startDt, endDt));
        req.put("demand_forecast_short_term_hourly", weatherApiService.fetchRawForecastItems(61, 125, baseDate, targetDate));

        List<Map<String, Object>> pvPast = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            if (s.getStationId() == null || s.getStationId() != 0) continue;
            ZonedDateTime st = s.getRecordedAt().atZone(KST);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tm",         st.format(iso));
            item.put("slot_start", st.format(iso));
            item.put("slot_end",   st.plusHours(1).format(iso));
            item.put("gen_kwh",    s.getPPv() != null ? s.getPPv() / 1000.0 : 0.0);
            pvPast.add(item);
        }
        req.put("pv_past_generation_hourly", pvPast);
        req.put("pv_past_weather_hourly", weatherApiService.fetchRawAsosItems("108", startDt, endDt));
        req.put("pv_forecast_short_term_hourly", weatherApiService.fetchRawForecastItems(61, 125, baseDate, targetDate));

        req.put("station_current_states", buildRawStationStates(allStations, callTime, iso));

        List<Map<String, Object>> tou = new ArrayList<>();
        for (int slot = 0; slot < 24; slot++) {
            ZonedDateTime st = targetDate.atTime(slot, 0).atZone(KST);
            String level; double price;
            if (slot >= 23 || slot < 9)                                            { level = "off_peak";  price = 93.3;  }
            else if ((slot >= 10 && slot < 12) || (slot >= 13 && slot < 17))      { level = "on_peak";   price = 229.5; }
            else                                                                    { level = "mid_peak";  price = 146.9; }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", slot);
            row.put("time_start", st.format(iso));
            row.put("time_end",   st.plusHours(1).format(iso));
            row.put("tou_level",  level);
            row.put("price_krw_per_kwh", price);
            tou.add(row);
        }
        req.put("tou_price_hourly", tou);

        req.put("grid_constraints", Map.of(
                "cluster_grid_limit_kw", 500.0,
                "station_grid_limit_kw", 120.0,
                "peak_limit_kw",         500.0));

        req.put("ess_constraints", Map.of(
                "ess_capacity_kwh_per_station", 100.0,
                "ess_min_soc",           0.1,
                "ess_max_soc",           0.9,
                "ess_max_charge_kw",     50.0,
                "ess_max_discharge_kw",  50.0,
                "round_trip_efficiency", 0.85));

        List<String> stationOrder = allStations.stream().map(ChargingStation::getName).toList();
        req.put("transfer_topology", Map.of(
                "transfer_enabled", true,
                "station_order", stationOrder,
                "adjacency_matrix_5x5", List.of(
                        List.of(0,1,1,0,0), List.of(1,0,1,1,0), List.of(1,1,0,1,1),
                        List.of(0,1,1,0,1), List.of(0,0,1,1,0)),
                "transfer_capacity_kw_matrix_5x5", List.of(
                        List.of(0,50,50,0,0), List.of(50,0,50,50,0), List.of(50,50,0,50,50),
                        List.of(0,50,50,0,50), List.of(0,0,50,50,0)),
                "transfer_loss_rate_matrix_5x5", List.of(
                        List.of(0.0,0.02,0.02,0.0,0.0), List.of(0.02,0.0,0.02,0.02,0.0),
                        List.of(0.02,0.02,0.0,0.02,0.02), List.of(0.0,0.02,0.02,0.0,0.02),
                        List.of(0.0,0.0,0.02,0.02,0.0))));

        return req;
    }

    // ─── Mock 데이터 ──────────────────────────────────────────────────

    public AiRequestDto buildMockAiRequest() {
        AiRequestDto request = new AiRequestDto();
        request.setRequestId("mock-" + Instant.now().toEpochMilli());
        request.setRequestTimestamp(Instant.now().toString());
        request.setScheduleTargetDate(LocalDate.now().plusDays(1).toString());
        request.setScheduleHorizonHours("24");
        request.setClusterState(buildClusterState());
        request.setStations(buildMockStations());
        return request;
    }

    public AiResponseDto buildMockAiResponse(String requestId) {
        AiResponseDto response = new AiResponseDto();
        response.setRequestId(requestId == null || requestId.isBlank() ? "mock-response" : requestId);
        response.setTimestamp(Instant.now().toString());

        AiResponseDto.Status status = new AiResponseDto.Status();
        status.setSuccess(true);
        status.setErrorCode(0);
        status.setMessage("Mock AI response generated");
        response.setStatus(status);

        response.setStationDayAheadSchedule(List.of(
                mockStationSchedule(1L),
                mockStationSchedule(2L)));
        return response;
    }

    // ─── private builders ─────────────────────────────────────────────

    private ClusterStateDto buildClusterState() {
        ClusterStateDto dto = new ClusterStateDto();
        dto.setTimeIndex(LocalDateTime.now().getHour());
        dto.setDayOfWeek(LocalDate.now().getDayOfWeek().getValue());
        dto.setTouPrice(174.0);
        dto.setGridLimit(200.0);
        dto.setTransferEnabled(true);
        return dto;
    }

    private List<StationDto> buildStations(MqttPayloadDto latest) {
        return latest.getStations().stream().map(this::toStationDto).toList();
    }

    private StationDto toStationDto(MqttStationDto s) {
        StationDto dto = new StationDto();
        dto.setStationId((long) s.getHeader().getStationId());

        CurrentStateDto state = new CurrentStateDto();
        state.setSoc(s.getPayload().getStateOfCharge().getSoc());
        state.setDemandCount((int) s.getPayload().getChargerStatus().stream()
                .filter(MqttChargerStatusDto::isHasDemand).count());
        state.setChargers(s.getPayload().getChargerStatus().stream()
                .map(c -> {
                    ChargerDto cd = new ChargerDto();
                    cd.setChargerId((long) c.getChargerId());
                    cd.setActive(c.isHasDemand());
                    cd.setPowerDemand(c.isHasDemand() ? 7.0 : 0.0);
                    return cd;
                }).toList());

        PowerDto power = new PowerDto();
        power.setPPv(s.getPayload().getPowerMetricsW().getPPv());
        power.setPLoad(s.getPayload().getPowerMetricsW().getPLoad());
        power.setPEss(s.getPayload().getPowerMetricsW().getPEss());
        power.setPGrid(s.getPayload().getPowerMetricsW().getPGrid());
        power.setPTr(s.getPayload().getPowerMetricsW().getPTr());
        state.setPower(power);
        dto.setCurrentState(state);

        ConstraintsDto constraints = new ConstraintsDto();
        constraints.setSocMin(0.2);
        constraints.setSocMax(0.9);
        constraints.setEssMaxCharge(15.0);
        constraints.setEssMaxDischarge(15.0);
        dto.setConstraints(constraints);

        return dto;
    }

    private List<StationDto> buildMockStations() {
        return List.of(
                mockStation(1L, 0.42, 2, 15.0, 23.0, 0.0, 8.0, 0.0),
                mockStation(2L, 0.68, 1, 7.0, 18.0, -4.0, 3.0, 0.0));
    }

    private StationDto mockStation(Long stationId, double soc, int demandCount,
                                   double pPv, double pLoad, double pEss, double pGrid, double pTr) {
        StationDto dto = new StationDto();
        dto.setStationId(stationId);

        CurrentStateDto state = new CurrentStateDto();
        state.setSoc(soc);
        state.setDemandCount(demandCount);

        List<ChargerDto> chargers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ChargerDto charger = new ChargerDto();
            charger.setChargerId(stationId * 100 + i);
            charger.setType("AC");
            charger.setActive(i <= demandCount);
            charger.setPowerDemand(i <= demandCount ? 7.0 : 0.0);
            chargers.add(charger);
        }
        state.setChargers(chargers);

        PowerDto power = new PowerDto();
        power.setPPv(pPv);
        power.setPLoad(pLoad);
        power.setPEss(pEss);
        power.setPGrid(pGrid);
        power.setPTr(pTr);
        state.setPower(power);
        dto.setCurrentState(state);

        ConstraintsDto constraints = new ConstraintsDto();
        constraints.setSocMin(0.2);
        constraints.setSocMax(0.9);
        constraints.setEssMaxCharge(15.0);
        constraints.setEssMaxDischarge(15.0);
        dto.setConstraints(constraints);

        return dto;
    }

    private StationScheduleDto mockStationSchedule(Long stationId) {
        StationScheduleDto stationSchedule = new StationScheduleDto();
        stationSchedule.setStationId(stationId);

        List<HourlyPlanDto> plans = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            HourlyPlanDto plan = new HourlyPlanDto();
            plan.setHour(hour);
            plan.setEssMode(hour < 8 ? "charge" : "discharge");
            plan.setEssPower(hour < 8 ? 6.0 : 4.0);
            plan.setGridUsage(hour < 8 ? 12.0 : 8.0);
            plan.setPvPriority(hour >= 10 && hour <= 16 ? 1.0 : 0.5);
            plan.setTransfer(Collections.emptyList());
            plans.add(plan);
        }
        stationSchedule.setHourlyPlan(plans);
        return stationSchedule;
    }

    private List<DemandPastDemandItemDto> buildDemandPastDemand(
            List<HourlySnapshot> snapshots, Map<Integer, String> nameMap, DateTimeFormatter iso) {
        List<DemandPastDemandItemDto> items = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            String name = nameMap.getOrDefault(s.getStationId(), "Station-" + s.getStationId());
            ZonedDateTime slotStart = s.getRecordedAt().atZone(KST);
            String tm     = slotStart.format(iso);
            String slotEnd = slotStart.plusHours(1).format(iso);
            double demandKwh = s.getPLoad() != null ? s.getPLoad() / 1000.0 : 0.0;
            items.add(new DemandPastDemandItemDto(tm, name, tm, slotEnd, demandKwh));
        }
        return items;
    }

    private List<PvPastGenerationItemDto> buildPvPastGeneration(
            List<HourlySnapshot> snapshots, DateTimeFormatter iso) {
        List<PvPastGenerationItemDto> items = new ArrayList<>();
        snapshots.stream()
                .filter(s -> s.getStationId() != null && s.getStationId() == 0)
                .forEach(s -> {
                    ZonedDateTime slotStart = s.getRecordedAt().atZone(KST);
                    String tm     = slotStart.format(iso);
                    String slotEnd = slotStart.plusHours(1).format(iso);
                    double genKwh = s.getPPv() != null ? s.getPPv() / 1000.0 : 0.0;
                    items.add(new PvPastGenerationItemDto(tm, tm, slotEnd, genKwh));
                });
        return items;
    }

    private List<StationCurrentStateItemDto> buildStationCurrentStates(
            List<ChargingStation> allStations, ZonedDateTime callTime, DateTimeFormatter iso) {
        MqttPayloadDto latest = dataProcessingService.getLatestData();
        String timestamp = callTime.format(iso);
        List<StationCurrentStateItemDto> states = new ArrayList<>();

        for (int i = 0; i < allStations.size(); i++) {
            ChargingStation station = allStations.get(i);
            StationCurrentStateItemDto state = new StationCurrentStateItemDto();
            state.setStationId(i);
            state.setStationName(station.getName());
            state.setPhysical(true);
            state.setTimestamp(timestamp);
            state.setErrorCode(0);

            boolean hasMqtt = latest != null && latest.getStations() != null
                    && i < latest.getStations().size();

            if (hasMqtt) {
                MqttStationDto mqttStation = latest.getStations().get(i);
                state.setPhysical(mqttStation.getHeader().isPhysical());
                state.setEssSoc(mqttStation.getPayload().getStateOfCharge().getSoc());

                List<MqttChargerStatusDto> csList = mqttStation.getPayload().getChargerStatus();
                state.setChargerCount(csList.size());

                List<ChargerStateItemDto> chargers = new ArrayList<>();
                for (MqttChargerStatusDto cs : csList) {
                    ChargerStateItemDto cd = new ChargerStateItemDto();
                    cd.setChargerId(String.format("ch-%03d", cs.getChargerId()));
                    cd.setChargerType("fast");
                    cd.setRatedPowerKw(100.0);
                    cd.setActive(cs.isHasDemand());
                    cd.setCurrentPowerKw(cs.isHasDemand() ? 7.0 : 0.0);
                    chargers.add(cd);
                }
                state.setChargers(chargers);
            } else {
                state.setEssSoc(0.5);
                state.setChargerCount(0);
                state.setChargers(new ArrayList<>());
            }
            states.add(state);
        }
        return states;
    }

    private List<TouPriceItemDto> buildTouPriceHourly(LocalDate targetDate, DateTimeFormatter iso) {
        List<TouPriceItemDto> prices = new ArrayList<>();
        for (int slot = 0; slot < 24; slot++) {
            ZonedDateTime start = targetDate.atTime(slot, 0).atZone(KST);
            ZonedDateTime end   = start.plusHours(1);
            String level; double price;
            if (slot >= 23 || slot < 9)                                        { level = "off_peak";  price = 93.3;  }
            else if ((slot >= 10 && slot < 12) || (slot >= 13 && slot < 17))  { level = "on_peak";   price = 229.5; }
            else                                                                { level = "mid_peak";  price = 146.9; }
            prices.add(new TouPriceItemDto(slot, start.format(iso), end.format(iso), level, price));
        }
        return prices;
    }

    private GridConstraintsDto buildGridConstraints() {
        GridConstraintsDto grid = new GridConstraintsDto();
        grid.setClusterGridLimitKw(500.0);
        grid.setStationGridLimitKw(120.0);
        grid.setPeakLimitKw(500.0);
        return grid;
    }

    private EssConstraintsDto buildEssConstraints() {
        EssConstraintsDto ess = new EssConstraintsDto();
        ess.setEssCapacityKwhPerStation(100.0);
        ess.setEssMinSoc(0.1);
        ess.setEssMaxSoc(0.9);
        ess.setEssMaxChargeKw(50.0);
        ess.setEssMaxDischargeKw(50.0);
        ess.setRoundTripEfficiency(0.85);
        return ess;
    }

    private TransferTopologyDto buildTransferTopology(List<ChargingStation> allStations) {
        TransferTopologyDto topology = new TransferTopologyDto();
        topology.setTransferEnabled(true);
        topology.setStationOrder(allStations.stream().map(ChargingStation::getName).toList());
        topology.setAdjacencyMatrix5x5(List.of(
                List.of(0,1,1,0,0), List.of(1,0,1,1,0), List.of(1,1,0,1,1),
                List.of(0,1,1,0,1), List.of(0,0,1,1,0)));
        topology.setTransferCapacityKwMatrix5x5(List.of(
                List.of(0,50,50,0,0), List.of(50,0,50,50,0), List.of(50,50,0,50,50),
                List.of(0,50,50,0,50), List.of(0,0,50,50,0)));
        topology.setTransferLossRateMatrix5x5(List.of(
                List.of(0.0,0.02,0.02,0.0,0.0), List.of(0.02,0.0,0.02,0.02,0.0),
                List.of(0.02,0.02,0.0,0.02,0.02), List.of(0.0,0.02,0.02,0.0,0.02),
                List.of(0.0,0.0,0.02,0.02,0.0)));
        return topology;
    }

    private List<Map<String, Object>> buildRawStationStates(
            List<ChargingStation> allStations, ZonedDateTime callTime, DateTimeFormatter iso) {
        MqttPayloadDto latest = dataProcessingService.getLatestData();
        String ts = callTime.format(iso);
        List<Map<String, Object>> states = new ArrayList<>();

        for (int i = 0; i < allStations.size(); i++) {
            ChargingStation station = allStations.get(i);
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("station_id",   i);
            state.put("station_name", station.getName());
            state.put("timestamp",    ts);
            state.put("error_code",   0);

            boolean hasMqtt = latest != null && latest.getStations() != null
                    && i < latest.getStations().size();

            if (hasMqtt) {
                MqttStationDto m = latest.getStations().get(i);
                state.put("is_physical", m.getHeader().isPhysical());
                state.put("ess_soc", m.getPayload().getStateOfCharge().getSoc());

                List<Map<String, Object>> chargers = new ArrayList<>();
                for (MqttChargerStatusDto cs : m.getPayload().getChargerStatus()) {
                    Map<String, Object> cd = new LinkedHashMap<>();
                    cd.put("charger_id",       String.format("ch-%03d", cs.getChargerId()));
                    cd.put("charger_type",     "fast");
                    cd.put("rated_power_kw",   100.0);
                    cd.put("is_active",        cs.isHasDemand());
                    cd.put("current_power_kw", cs.isHasDemand() ? 7.0 : 0.0);
                    chargers.add(cd);
                }
                state.put("charger_count", chargers.size());
                state.put("chargers", chargers);
            } else {
                state.put("is_physical",   true);
                state.put("ess_soc",       0.5);
                state.put("charger_count", 0);
                state.put("chargers",      new ArrayList<>());
            }
            states.add(state);
        }
        return states;
    }
}
