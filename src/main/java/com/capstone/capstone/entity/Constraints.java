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
    private Double essMaxCharge;
    private Double essMaxDischarge;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", unique = true)
    private ChargingStation station;
}