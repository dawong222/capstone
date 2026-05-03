package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TransferTopologyDto {

    @JsonProperty("transfer_enabled")
    private boolean transferEnabled;

    @JsonProperty("station_order")
    private List<String> stationOrder;

    @JsonProperty("adjacency_matrix_5x5")
    private List<List<Integer>> adjacencyMatrix5x5;

    @JsonProperty("transfer_capacity_kw_matrix_5x5")
    private List<List<Integer>> transferCapacityKwMatrix5x5;

    @JsonProperty("transfer_loss_rate_matrix_5x5")
    private List<List<Double>> transferLossRateMatrix5x5;
}
