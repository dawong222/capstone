package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Constraints {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double socMin;
    private Double socMax;
    @Column(name = "ess_max_charge_w")
    private Double essMaxChargeW;
    @Column(name = "ess_max_discharge_w")
    private Double essMaxDischargeW;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", unique = true)
    private ChargingStation station;
}