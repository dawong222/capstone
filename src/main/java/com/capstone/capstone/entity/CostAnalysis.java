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
public class CostAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private float costWithoutAi;
    private float costWithAi;
    private float savings;

    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private ChargingStation station;
}