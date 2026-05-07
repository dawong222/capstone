package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ClusterState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer timeIndex;      // 0~23
    private String touLevel;        // off_peak / mid_peak / on_peak
    private Double touPrice;
    private Double gridLimit;
    private Boolean transferEnabled;
    private Integer dayOfWeek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_job_id")
    private ScheduleJob scheduleJob;
}
