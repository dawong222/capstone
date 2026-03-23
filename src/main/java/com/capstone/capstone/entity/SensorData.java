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
public class SensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private float powerUsage;
    private float essSoc;
    private float temperature;
    private float voltage;

    private LocalDateTime timestamp;

    @ManyToOne
    @JoinColumn(name = "station_id")
    private ChargingStation station;
}
