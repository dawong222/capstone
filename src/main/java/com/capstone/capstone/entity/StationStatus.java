package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StationStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private float currentPowerUsage;
    private float currentEssSoc;
    private float currentPvGeneration;
    private float currentEvDemand;
    private int activeEvCount;

    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private ChargingStation station;
}
