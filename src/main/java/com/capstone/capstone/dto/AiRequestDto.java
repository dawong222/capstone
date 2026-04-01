package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AiRequestDto {

    private String requestId;
    private String timestamp;
    private ClusterStateDto clusterState;
    private List<StationDto> stations;

}