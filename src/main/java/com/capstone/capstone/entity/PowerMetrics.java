package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PowerMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double pPv;
    private Double pLoad;
    private Double pEss;
    private Double pGrid;
    private Double pTr;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_state_id", unique = true)
    private StationState stationState;
}