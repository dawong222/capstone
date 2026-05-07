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
    private Double essMaxCharge;       // kW
    private Double essMaxDischarge;    // kW
    private Double essCapacityKwh;     // kWh
    private Double gridImportLimitKw;
    private Double gridExportLimitKw;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", unique = true)
    private ChargingStation station;
}
