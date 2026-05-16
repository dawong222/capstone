package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_job_id", "station_index", "hour"}))
public class StationDemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_job_id")
    private ScheduleJob scheduleJob;

    private Integer stationIndex;
    private Integer hour;
    private Double predictedDemandKwh;
}
