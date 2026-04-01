package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class PowerMetricsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double pPvW;
    private Double pLoadW;
    private Double pEssW;
    private Double pGridW;
    private Double pTrW;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_state_id", unique = true)
    private StationStateLog stationState;
}