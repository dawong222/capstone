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
public class SchedulingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private float essAction;
    private float pvAction;
    private float gridUsage;
    private float selfConsumption;
    private float cost;
    private float reward;

    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private ChargingStation station;
}