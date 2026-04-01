package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class StationStateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double soc;
    private Integer demandCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_job_id")
    private ScheduleJob scheduleJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private ChargingStation station;

    @OneToOne(mappedBy = "stationState", cascade = CascadeType.ALL)
    private PowerMetricsLog powerMetrics;

    @OneToMany(mappedBy = "stationState", cascade = CascadeType.ALL)
    private List<ChargerStateLog> chargerStates;
}