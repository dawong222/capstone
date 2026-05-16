package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"station_id", "charger_index"}))
public class Charger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charger_index", nullable = false)
    private Integer chargerIndex;   // MQTT chargerId (0-based, 스테이션 내 인덱스)

    private String chargerType;     // "fast" / "slow"
    private Double ratedPowerKw;    // 정격 출력 (kW)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private ChargingStation station;
}
