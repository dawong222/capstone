package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
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
import java.time.temporal.ChronoUnit;
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

        // 과거 7일치 HourlySnapshot 조회
        LocalDateTime snapshotFrom = LocalDateTime.now().minusDays(7).truncatedTo(ChronoUnit.HOURS);
        LocalDateTime snapshotTo   = LocalDateTime.now();
        List<HourlySnapshot> snapshots = snapshotRepository
                .findByRecordedAtBetweenOrderByRecordedAt(snapshotFrom, snapshotTo);

        req.put("demand_past_demand_hourly",  buildDemandPastDemandHourly(snapshots, iso));
        req.put("pv_past_generation_hourly",  buildPvPastGenerationHourly(snapshots, iso));

        // ASOS 과거 날씨 (7일치, 강남 = ASOS 108)
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        String asosStart = d.minusDays(7).format(dateFmt);
        String asosEnd   = d.format(dateFmt);
        List<Map<String, Object>> asosRaw = weatherApiService.fetchRawAsosItems("108", asosStart, asosEnd);
        req.put("demand_past_weather_hourly", buildAsosWeatherHourly(asosRaw, "ASOS_108", "Gangnam", iso));
        req.put("pv_past_weather_hourly",     buildAsosWeatherHourly(asosRaw, "ASOS_108", "Gangnam/PV site", iso));

        // 단기예보 (내일 24시간, 강남 격자 nx=61 ny=125)
        List<Map<String, Object>> forecastRaw =
                weatherApiService.fetchRawForecastItems(61, 125, d.format(dateFmt), targetDate);
        req.put("demand_forecast_short_term_hourly", buildForecastWeatherHourly(forecastRaw, "Gangnam forecast grid"));
        req.put("pv_forecast_short_term_hourly",     buildForecastWeatherHourly(forecastRaw, "Gangnam PV forecast grid"));

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
                    double power = cs.isHasDemand() ? 7.0 : 0.0;
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("charger_id",       cs.getChargerId());
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

    // ─── 날씨 데이터 빌더 ────────────────────────────────────────────────

    private List<Map<String, Object>> buildAsosWeatherHourly(
            List<Map<String, Object>> asosRaw, String source, String locationName, DateTimeFormatter iso) {
        DateTimeFormatter asosFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : asosRaw) {
            String tmRaw = String.valueOf(r.getOrDefault("tm", ""));
            String ts;
            try {
                ts = LocalDateTime.parse(tmRaw, asosFmt).atZone(KST).format(iso);
            } catch (Exception e) {
                ts = tmRaw;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp",               ts);
            row.put("tm",                      ts);
            row.put("source",                  source);
            row.put("location_name",           locationName);
            row.put("ta",                      toDouble(r, "ta"));
            row.put("temperature_c",           toDouble(r, "ta"));
            row.put("rn",                      toDouble(r, "rn"));
            row.put("precipitation_mm",        toDouble(r, "rn"));
            row.put("ws",                      toDouble(r, "ws"));
            row.put("wind_speed_ms",           toDouble(r, "ws"));
            row.put("wd",                      toDouble(r, "wd"));
            row.put("wind_direction_deg",      toDouble(r, "wd"));
            row.put("hm",                      toDouble(r, "hm"));
            row.put("humidity_pct",            toDouble(r, "hm"));
            row.put("pa",                      toDouble(r, "pa"));
            row.put("pressure_hpa",            toDouble(r, "pa"));
            row.put("ps",                      toDouble(r, "ps"));
            row.put("sea_level_pressure_hpa",  toDouble(r, "ps"));
            row.put("ss",                      toDouble(r, "ss"));
            row.put("sunshine_hours",          toDouble(r, "ss"));
            row.put("icsr",                    toDouble(r, "icsr"));
            row.put("solar_radiation_mj_m2",   toDouble(r, "icsr"));
            row.put("dsnw",                    toDouble(r, "dsnw"));
            row.put("snow_cm",                 toDouble(r, "dsnw"));
            row.put("hr3Fhsc",                 toDouble(r, "hr3Fhsc"));
            row.put("new_snow_3h_cm",          toDouble(r, "hr3Fhsc"));
            row.put("dc10Tca",                 toDouble(r, "dc10Tca"));
            row.put("cloud_amount",            toDouble(r, "dc10Tca"));
            list.add(row);
        }
        return list;
    }

    private List<Map<String, Object>> buildForecastWeatherHourly(
            List<Map<String, Object>> forecastRaw, String forecastLocation) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : forecastRaw) {
            String ts = String.valueOf(r.getOrDefault("tm", ""));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp",        ts);
            row.put("tmef",             ts);
            row.put("forecast_location", forecastLocation);
            row.put("TMP",  toDouble(r, "tmp"));
            row.put("POP",  toDouble(r, "pop"));
            row.put("PTY",  toDouble(r, "pty").intValue());
            row.put("PCP",  toDouble(r, "pcp"));
            row.put("SNO",  toDouble(r, "sno"));
            row.put("REH",  toDouble(r, "reh"));
            row.put("SKY",  toDouble(r, "sky").intValue());
            row.put("WSD",  toDouble(r, "wsd"));
            row.put("VEC",  toDouble(r, "vec"));
            row.put("UUU",  toDouble(r, "uuu"));
            row.put("VVV",  toDouble(r, "vvv"));
            row.put("TMN",  toDouble(r, "tmn"));
            row.put("TMX",  toDouble(r, "tmx"));
            row.put("LGT",  toDouble(r, "lgt").intValue());
            list.add(row);
        }
        return list;
    }

    private Double toDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString().trim()); } catch (Exception e) { return 0.0; }
    }

    // ─── 과거 수요/발전 데이터 빌더 ──────────────────────────────────────

    private List<Map<String, Object>> buildDemandPastDemandHourly(
            List<HourlySnapshot> snapshots, DateTimeFormatter iso) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            if (s.getStation() == null || s.getPLoad() == null) continue;
            ZonedDateTime slotStart = s.getRecordedAt().atZone(KST);
            ZonedDateTime slotEnd   = slotStart.plusHours(1);
            String ts = slotStart.format(iso);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("station_id",   s.getStation().getStationIndex());
            row.put("station_name", s.getStation().getName());
            row.put("timestamp",    ts);
            row.put("tm",           ts);
            row.put("slot_start",   ts);
            row.put("slot_end",     slotEnd.format(iso));
            row.put("demand_kwh",   s.getPLoad() / 1000.0);
            list.add(row);
        }
        return list;
    }

    private List<Map<String, Object>> buildPvPastGenerationHourly(
            List<HourlySnapshot> snapshots, DateTimeFormatter iso) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            // PV는 물리 스테이션(stationIndex=0)에서만 측정
            if (s.getStation() == null || !Integer.valueOf(0).equals(s.getStation().getStationIndex())) continue;
            if (s.getPPv() == null) continue;
            ZonedDateTime slotStart = s.getRecordedAt().atZone(KST);
            ZonedDateTime slotEnd   = slotStart.plusHours(1);
            String ts = slotStart.format(iso);
            double genKwh = s.getPPv() / 1000.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp",          ts);
            row.put("tm",                 ts);
            row.put("slot_start",         ts);
            row.put("slot_end",           slotEnd.format(iso));
            row.put("gen_kwh",            genKwh);
            row.put("pv_generation_kwh",  genKwh);
            list.add(row);
        }
        return list;
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


}
