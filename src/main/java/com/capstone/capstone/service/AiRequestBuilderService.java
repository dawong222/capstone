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

        List<ChargingStation> allStations = stationRepository.findAll();
        allStations.sort(Comparator.comparing(ChargingStation::getId));
        MqttPayloadDto latest = dataProcessingService.getLatestData();
        String ts = callTime.format(iso);

        Map<String, Object> req = new LinkedHashMap<>();

        req.put("request_id", String.format("backend-schedule-request-%s-%s-0001",
                d.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                callTime.format(DateTimeFormatter.ofPattern("HHmmss"))));
        req.put("request_timestamp", ts);
        req.put("schedule_target_date", targetDate.toString());
        req.put("schedule_horizon_hours", 24);
        req.put("schedule_mode", "day-ahead");
        req.put("timezone", "Asia/Seoul");

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("start", targetDate.atStartOfDay(KST).format(iso));
        window.put("end",   targetDate.plusDays(1).atStartOfDay(KST).format(iso));
        window.put("slot_unit",  "hour");
        window.put("slot_count", 24);
        window.put("slot_definition", "slot 0 = 00:00~01:00, slot 23 = 23:00~24:00");
        req.put("target_window", window);

        List<Integer> physicalIds = new ArrayList<>();
        List<Integer> simulatedIds = new ArrayList<>();
        for (int i = 0; i < allStations.size(); i++) {
            boolean isPhysical = latest != null && latest.getStations() != null
                    && i < latest.getStations().size()
                    && latest.getStations().get(i).getHeader().isPhysical();
            if (isPhysical) physicalIds.add(i);
            else simulatedIds.add(i);
        }
        Map<String, Object> clusterState = new LinkedHashMap<>();
        clusterState.put("station_count", allStations.size());
        clusterState.put("physical_station_ids", physicalIds);
        clusterState.put("simulated_station_ids", simulatedIds);
        clusterState.put("grid_limit_kw", 200.0);
        req.put("cluster_state", clusterState);

        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("soc_min", 0.1);
        constraints.put("soc_max", 0.9);
        req.put("constraints", constraints);

        Map<String, Object> transfer = new LinkedHashMap<>();
        transfer.put("enabled", true);
        transfer.put("capacity_kw", 30.0);
        transfer.put("loss_rate", 0.03);
        req.put("transfer", transfer);

        Map<String, Object> gridConstraints = new LinkedHashMap<>();
        gridConstraints.put("cluster_grid_limit_kw", 200.0);
        gridConstraints.put("station_grid_limit_kw", 120.0);
        gridConstraints.put("peak_limit_kw", 200.0);
        req.put("grid_constraints", gridConstraints);

        Map<String, Object> essConstraints = new LinkedHashMap<>();
        essConstraints.put("ess_capacity_kwh_per_station", 100.0);
        essConstraints.put("ess_min_soc", 0.1);
        essConstraints.put("ess_max_soc", 0.9);
        essConstraints.put("ess_max_charge_kw", 50.0);
        essConstraints.put("ess_max_discharge_kw", 50.0);
        essConstraints.put("round_trip_efficiency", 0.9025);
        req.put("ess_constraints", essConstraints);

        List<String> stationOrder = allStations.stream().map(ChargingStation::getName).toList();
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("transfer_enabled", true);
        topology.put("station_order", stationOrder);
        topology.put("adjacency_matrix_5x5", List.of(
                List.of(0,1,1,0,0), List.of(1,0,1,1,0), List.of(1,1,0,1,1),
                List.of(0,1,1,0,1), List.of(0,0,1,1,0)));
        topology.put("transfer_capacity_kw_matrix_5x5", List.of(
                List.of(0,30,30,0,0), List.of(30,0,30,30,0), List.of(30,30,0,30,30),
                List.of(0,30,30,0,30), List.of(0,0,30,30,0)));
        topology.put("transfer_loss_rate_matrix_5x5", List.of(
                List.of(0.0,0.03,0.03,0.0,0.0), List.of(0.03,0.0,0.03,0.03,0.0),
                List.of(0.03,0.03,0.0,0.03,0.03), List.of(0.0,0.03,0.03,0.0,0.03),
                List.of(0.0,0.0,0.03,0.03,0.0)));
        req.put("transfer_topology", topology);

        Map<String, Double> touPriceMap = new LinkedHashMap<>();
        for (int slot = 0; slot < 24; slot++) touPriceMap.put(String.valueOf(slot), getTouPrice(slot));
        req.put("tou_price_krw_per_kwh", touPriceMap);

        List<Map<String, Object>> tou = new ArrayList<>();
        for (int slot = 0; slot < 24; slot++) {
            ZonedDateTime st = targetDate.atTime(slot, 0).atZone(KST);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", slot);
            row.put("hour", slot);
            row.put("slot_label", String.format("%02d:00~%02d:00", slot, slot + 1));
            row.put("time_start", st.format(iso));
            row.put("time_end",   st.plusHours(1).format(iso));
            row.put("tou_level",  getTouLevel(slot));
            row.put("price_krw_per_kwh", getTouPrice(slot));
            tou.add(row);
        }
        req.put("tou_price_hourly", tou);

        List<Map<String, Object>> stationList = buildRawStationList(allStations, latest, ts);
        req.put("stations", stationList);
        req.put("station_current_states", buildRawStationList(allStations, latest, ts));

        return req;
    }

    private String getTouLevel(int slot) {
        if (slot <= 7 || slot >= 22) return "off_peak";
        if (slot == 11 || (slot >= 13 && slot <= 17)) return "on_peak";
        return "mid_peak";
    }

    private double getTouPrice(int slot) {
        if (slot <= 7 || slot >= 22) return 83.1;
        if (slot == 11 || (slot >= 13 && slot <= 17)) return 270.8;
        return 140.0;
    }

    private List<Map<String, Object>> buildRawStationList(
            List<ChargingStation> allStations, MqttPayloadDto latest, String ts) {
        List<Map<String, Object>> stations = new ArrayList<>();
        for (int i = 0; i < allStations.size(); i++) {
            ChargingStation station = allStations.get(i);
            boolean hasMqtt = latest != null && latest.getStations() != null
                    && i < latest.getStations().size();

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("station_id", i);
            s.put("station_name", station.getName());
            s.put("is_physical", hasMqtt && latest.getStations().get(i).getHeader().isPhysical());

            Map<String, Object> state = new LinkedHashMap<>();
            state.put("timestamp", ts);
            if (hasMqtt) {
                MqttStationDto m = latest.getStations().get(i);
                double soc = m.getPayload().getStateOfCharge().getSoc();
                state.put("soc", soc);
                state.put("ess_soc", soc);
                state.put("p_pv_kw",   m.getPayload().getPowerMetricsW().getPPv()  / 1000.0);
                state.put("p_load_kw", m.getPayload().getPowerMetricsW().getPLoad() / 1000.0);
                state.put("p_grid_kw", m.getPayload().getPowerMetricsW().getPGrid() / 1000.0);
            } else {
                state.put("soc", 0.5);
                state.put("ess_soc", 0.5);
                state.put("p_pv_kw",   0.0);
                state.put("p_load_kw", 0.0);
                state.put("p_grid_kw", 0.0);
            }
            state.put("error_code", 0);
            s.put("current_state", state);

            s.put("ess_capacity_kwh",     100.0);
            s.put("ess_max_charge_kw",     50.0);
            s.put("ess_max_discharge_kw",  50.0);
            s.put("ess_charge_efficiency",    0.95);
            s.put("ess_discharge_efficiency", 0.95);

            List<Map<String, Object>> chargers = new ArrayList<>();
            if (hasMqtt) {
                for (MqttChargerStatusDto cs : latest.getStations().get(i).getPayload().getChargerStatus()) {
                    String type = (cs.getChargerId() == 0 || cs.getChargerId() == 3) ? "fast" : "slow";
                    double ratedPower = "fast".equals(type) ? 50.0 : 7.0;
                    double power = cs.isHasDemand() ? 7.0 : 0.0;
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("charger_id",       cs.getChargerId());
                    c.put("type",             type);
                    c.put("charger_type",     type);
                    c.put("rated_power_kw",   ratedPower);
                    c.put("is_active",        cs.isHasDemand());
                    c.put("has_demand",       cs.isHasDemand());
                    c.put("power_demand_kw",  power);
                    c.put("current_power_kw", power);
                    chargers.add(c);
                }
            }
            s.put("chargers", chargers);
            stations.add(s);
        }
        return stations;
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

}
