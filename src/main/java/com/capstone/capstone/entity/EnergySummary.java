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
public class EnergySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private float totalPowerUsage;
    private float totalCost;
    private float totalPvUsed;
    private float totalGridUsed;

    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private ChargingStation station;
}