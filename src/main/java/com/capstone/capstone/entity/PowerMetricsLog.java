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

    @Column(name = "p_pv_w")
    private Double pPvW;
    @Column(name = "p_load_w")
    private Double pLoadW;
    @Column(name = "p_ess_w")
    private Double pEssW;
    @Column(name = "p_grid_w")
    private Double pGridW;
    @Column(name = "p_tr_w")
    private Double pTrW;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_state_id", unique = true)
    private StationStateLog stationState;
}