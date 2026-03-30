package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Charger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int chargerId;
    private boolean hasDemand;

    @ManyToOne
    @JoinColumn(name="station_id")
    private ChargingStation station;
}
