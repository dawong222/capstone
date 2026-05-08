package com.capstone.capstone.service;

import com.capstone.capstone.dto.AiResponseDto;
import com.capstone.capstone.dto.HourlyPlanDto;
import com.capstone.capstone.dto.StationScheduleDto;
import com.capstone.capstone.dto.TransferDto;
import com.capstone.capstone.mqtt.MqttPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleMqttPublisherService {

    private final MqttPublisher mqttPublisher;
    private final ObjectMapper objectMapper;

    public void publishSchedule(AiResponseDto dto) {
        if (dto.getStationDayAheadSchedule() == null) return;

        for (StationScheduleDto station : dto.getStationDayAheadSchedule()) {
            int stationIdx = station.getStationId() != null ? station.getStationId().intValue() : -1;
            if (stationIdx < 0) continue;

            String topic = "/control/station/" + stationIdx;

            List<Map<String, Object>> hourlyPlan = new ArrayList<>();
            List<HourlyPlanDto> plans = station.getHourlyPlan() != null ? station.getHourlyPlan() : List.of();
            for (HourlyPlanDto plan : plans) {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("hour", plan.getHour());
                slot.put("ess_mode", plan.getEssMode());
                slot.put("ess_power_kw", plan.getEssPower());
                slot.put("grid_usage_kw", plan.getGridUsage());

                List<Map<String, Object>> transfers = new ArrayList<>();
                if (plan.getTransfer() != null) {
                    for (TransferDto t : plan.getTransfer()) {
                        Map<String, Object> tr = new LinkedHashMap<>();
                        tr.put("target_station_id", t.getTargetStationId());
                        tr.put("transfer_power_kw", t.getPower());
                        transfers.add(tr);
                    }
                }
                slot.put("transfer", transfers);
                hourlyPlan.add(slot);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "day_ahead_schedule");
            payload.put("station_id", stationIdx);
            payload.put("hourly_plan", hourlyPlan);

            try {
                String json = objectMapper.writeValueAsString(payload);
                mqttPublisher.publish(topic, json);
                log.info("[스케줄 MQTT 발행] topic={}, 시간 수={}", topic, hourlyPlan.size());
            } catch (Exception e) {
                log.error("[스케줄 MQTT 발행 실패] stationIdx={} : {}", stationIdx, e.getMessage());
            }
        }
    }
}
