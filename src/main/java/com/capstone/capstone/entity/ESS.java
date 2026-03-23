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
public class ESS {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private float capacity;

    private float currentSoc;

    private LocalDateTime createdAt;

    @OneToOne
    @JoinColumn(name = "station_id", unique = true)
    private ChargingStation station;
}