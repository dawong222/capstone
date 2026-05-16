package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ScheduleMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_job_id", unique = true)
    private ScheduleJob scheduleJob;

    private Double gridOnlyCostKrw;
    private Double sacCostWithTransferKrw;
    private Double costReductionKrw;
    private Double costReductionPct;
    private Double totalDemandKwh;
    private Double totalPvKwh;
    private Double totalGridUsageKwh;
    private Double totalSelfSupplyKwh;
    private Double totalPvLostKwh;
    private Double totalTransferOutKwh;
    private Double totalTransferInKwh;
    private Double totalTransferLossKwh;
    private Double maxClusterGridUsageKwhPerHour;
    private Integer peakViolationSlots;
    private Double socMin;
    private Double socMax;
    private Double totalRewardBeforeTransfer;
    private String device;
    private Boolean torchCudaAvailable;
    private String cudaDeviceName;
    private Boolean transferPostprocessEnabled;
}
