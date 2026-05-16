package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double transferPowerKw;
    private Double receivedPowerKw;
    private Double lossPowerKw;
    private Double transferEnergyKwh;
    private Double receivedEnergyKwh;
    private Double lossEnergyKwh;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hourly_plan_id")
    private HourlyPlan hourlyPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_station_id")
    private ChargingStation targetStation;
}
