package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ScheduleJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String requestId;

    private LocalDate scheduleTargetDate;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @OneToMany(mappedBy = "scheduleJob", cascade = CascadeType.ALL)
    private List<ScheduleResult> results;

    @OneToMany(mappedBy = "scheduleJob", cascade = CascadeType.ALL)
    private List<ClusterState> clusterStates;
}
