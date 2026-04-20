package com.capstone.capstone.service;


import com.capstone.capstone.dto.AiRequestDto;
import com.capstone.capstone.dto.ChargerStatusDto;
import com.capstone.capstone.dto.IoTDataDto;
import com.capstone.capstone.dto.StationDto;
import com.capstone.capstone.entity.Charger;
import com.capstone.capstone.entity.ChargingStation;
import com.capstone.capstone.entity.PowerMetrics;
import com.capstone.capstone.entity.StationState;
import com.capstone.capstone.repository.ChargerRepository;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.PowerMetricsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DataProcessingService {
    //IoT 기기에서 받은 JSON 데이터를 DB에 저장

    private final ObjectMapper objectMapper;
    private final ChargingStationRepository repository;

    public DataProcessingService(ObjectMapper objectMapper, ChargingStationRepository repository) {
        this.objectMapper = objectMapper;
        this.repository =  repository;
    }

    private final SchedulingService schedulingService; // 다음 단계 연결용

    public void process(String payload) {

        try{
            AiRequestDto request = objectMapper.readValue(payload, AiRequestDto.class);
            //JSON -> AiRequestDTO
            for (StationDto stationDto : request.getStations()) {
                ChargingStation station = toEntity(stationDto);
                repository.save(station);
            }
        }catch (JsonProcessingException e){
            throw new RuntimeException("JSON 파싱 실패",e);
        }
    }

    private ChargingStation toEntity(StationDto stationDto) {
        Long id = (long) stationDto.getStationId();

        ChargingStation station = repository.findById(id).orElse(new ChargingStation());

        station.setId(id);

        StationState state = new StationState();

        state.setStation(station);
        state.setSoc(stationDto.getCurrentState().getSoc());

        PowerMetrics power = new PowerMetrics();
        power.setPPv(stationDto.getCurrentState().getPower().getPPv());
        power.setPLoad(stationDto.getCurrentState().getPower().getPLoad());
        power.setPEss(stationDto.getCurrentState().getPower().getPEss());
        power.setPGrid(stationDto.getCurrentState().getPower().getPGrid());
        power.setPTr(stationDto.getCurrentState().getPower().getPTr());

        state.setTimestamp()

    }
}


