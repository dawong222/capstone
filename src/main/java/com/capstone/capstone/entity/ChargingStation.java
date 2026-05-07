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

    @Column(unique = true)
    private Integer stationIndex;   // MQTT/AI 0-based 인덱스

    private Double essCapacityKwh;
    private Boolean isActive;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL)
    private List<Charger> chargers;

    @OneToOne(mappedBy = "station", cascade = CascadeType.ALL)
    private Constraints constraints;

    @OneToOne(mappedBy = "station", cascade = CascadeType.ALL)
    private StationState currentState;
}
