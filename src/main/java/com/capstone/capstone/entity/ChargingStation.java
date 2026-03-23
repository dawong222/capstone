package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String location;

    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "station", cascade = CascadeType.ALL)
    private ESS ess;

    @OneToMany(mappedBy = "station")
    private List<SensorData> sensorDataList;

    @OneToMany(mappedBy = "station")
    private List<PVGeneration> pvGenerationList;

    @OneToMany(mappedBy = "station")
    private List<EVRequest> evRequestList;

    @OneToMany(mappedBy = "station")
    private List<SchedulingResult> schedulingResults;

    @OneToMany(mappedBy = "station")
    private List<StationStatus> stationStatuses;

    @OneToMany(mappedBy = "station")
    private List<EnergySummary> energySummaries;

    @OneToMany(mappedBy = "station")
    private List<CostAnalysis> costAnalyses;
}