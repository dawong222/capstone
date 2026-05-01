package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiRequestDto {

    private String requestId;
    private String requestTimestamp;

    private String scheduleTargetDate;
    private String scheduleHorizonHours;

    private ClusterStateDto clusterState;
    private List<StationDto> stations;

    private Map<String, Object> weather;


}