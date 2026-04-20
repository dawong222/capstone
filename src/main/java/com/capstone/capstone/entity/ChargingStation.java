package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ChargingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String location;
    private Boolean isActive;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL)
    private List<Charger> chargers;

    @OneToOne(mappedBy = "station", cascade = CascadeType.ALL)
    private Constraints constraints;

    @OneToOne(mappedBy = "station", cascade = CascadeType.ALL)
    private StationState currentState;
}