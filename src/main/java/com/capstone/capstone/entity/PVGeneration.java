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
public class PVGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private float generation;

    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private ChargingStation station;
}