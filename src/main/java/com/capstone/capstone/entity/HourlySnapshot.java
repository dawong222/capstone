package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
public class HourlySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private ChargingStation station;

    private LocalDateTime recordedAt;
    private Double soc;
    private Integer demandCount;
    private Double pPv;
    private Double pLoad;
    private Double pEss;
    private Double pGrid;
    private Double pTr;
    private Double capacityWh;
}
