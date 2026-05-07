package com.capstone.capstone.service;

import com.capstone.capstone.dto.mqtt.MqttChargerStatusDto;
import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.dto.mqtt.MqttStationDto;
import com.capstone.capstone.entity.*;
import com.capstone.capstone.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryPersistenceService {

    private final ChargingStationRepository stationRepository;
    private final StationStateRepository stationStateRepository;
    private final PowerMetricsRepository powerMetricsRepository;
    private final ChargerRepository chargerRepository;
    private final ChargerStateRepository chargerStateRepository;

    @Transactional
    public void persist(MqttPayloadDto payload) {
        if (payload == null || payload.getStations() == null) return;

        List<ChargingStation> allStations = stationRepository.findAll();
        Map<Integer, ChargingStation> indexToStation = allStations.stream()
                .filter(s -> s.getStationIndex() != null)
                .collect(Collectors.toMap(ChargingStation::getStationIndex, s -> s));

        LocalDateTime now = LocalDateTime.now();

        for (MqttStationDto mqttStation : payload.getStations()) {
            int stationIndex = mqttStation.getHeader().getStationId();
            ChargingStation station = indexToStation.get(stationIndex);
            if (station == null) {
                log.warn("[텔레메트리 저장] stationIndex={} 매핑 실패 - 스킵", stationIndex);
                continue;
            }

            // StationState upsert
            StationState state = stationStateRepository.findByStationId(station.getId())
                    .orElse(new StationState());
            state.setStation(station);
            state.setSoc(mqttStation.getPayload().getStateOfCharge().getSoc());
            state.setDemandCount((int) mqttStation.getPayload().getChargerStatus().stream()
                    .filter(MqttChargerStatusDto::isHasDemand).count());
            state.setUpdatedAt(now);
            StationState savedState = stationStateRepository.save(state);

            // PowerMetrics upsert
            PowerMetrics metrics = powerMetricsRepository.findByStationStateId(savedState.getId())
                    .orElse(new PowerMetrics());
            metrics.setStationState(savedState);
            metrics.setPPv(mqttStation.getPayload().getPowerMetricsW().getPPv());
            metrics.setPLoad(mqttStation.getPayload().getPowerMetricsW().getPLoad());
            metrics.setPEss(mqttStation.getPayload().getPowerMetricsW().getPEss());
            metrics.setPGrid(mqttStation.getPayload().getPowerMetricsW().getPGrid());
            metrics.setPTr(mqttStation.getPayload().getPowerMetricsW().getPTr());
            powerMetricsRepository.save(metrics);

            // ChargerState upsert (충전기별)
            for (MqttChargerStatusDto cs : mqttStation.getPayload().getChargerStatus()) {
                Charger charger = chargerRepository
                        .findByStationIdAndChargerIndex(station.getId(), cs.getChargerId())
                        .orElse(null);
                if (charger == null) {
                    log.warn("[텔레메트리 저장] stationIndex={}, chargerIndex={} 매핑 실패 - 스킵",
                            stationIndex, cs.getChargerId());
                    continue;
                }

                ChargerState chargerState = chargerStateRepository
                        .findByStationStateIdAndChargerId(savedState.getId(), charger.getId())
                        .orElse(new ChargerState());
                chargerState.setStationState(savedState);
                chargerState.setCharger(charger);
                chargerState.setIsActive(cs.isHasDemand());
                chargerState.setPowerDemand(cs.isHasDemand() ? 7.0 : 0.0);
                chargerStateRepository.save(chargerState);
            }
        }
    }
}
