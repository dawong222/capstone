-- ============================================================
-- DB 초기화 스크립트 (capstonedb / PostgreSQL)
-- 실행 전 주의: 기존 데이터 전부 삭제됩니다.
-- 실행: psql -h <host> -U capstone -d capstonedb -f reset_schema.sql
-- ============================================================

-- 기존 테이블 삭제 (FK 순서 역순)
DROP TABLE IF EXISTS station_demand_forecast CASCADE;
DROP TABLE IF EXISTS cluster_forecast CASCADE;
DROP TABLE IF EXISTS transfer CASCADE;
DROP TABLE IF EXISTS hourly_plan CASCADE;
DROP TABLE IF EXISTS schedule_result CASCADE;
DROP TABLE IF EXISTS schedule_metrics CASCADE;
DROP TABLE IF EXISTS schedule_job CASCADE;
DROP TABLE IF EXISTS hourly_snapshot CASCADE;
DROP TABLE IF EXISTS charger_state CASCADE;
DROP TABLE IF EXISTS power_metrics CASCADE;
DROP TABLE IF EXISTS station_state CASCADE;
DROP TABLE IF EXISTS constraints CASCADE;
DROP TABLE IF EXISTS charger CASCADE;
DROP TABLE IF EXISTS charging_station CASCADE;

-- ============================================================
-- 1. 충전 스테이션 (정적 설정)
-- ============================================================
CREATE TABLE charging_station (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    location         VARCHAR(255),
    station_index    INTEGER UNIQUE,           -- MQTT/AI 0-based 인덱스
    ess_capacity_kwh DOUBLE PRECISION DEFAULT 100.0,
    is_active        BOOLEAN DEFAULT TRUE
);

-- ============================================================
-- 2. 충전기 (스테이션당 5개, charger_index = MQTT chargerId)
--    charger_type : "fast" (50kW) / "slow" (7kW)
-- ============================================================
CREATE TABLE charger (
    id              BIGSERIAL PRIMARY KEY,
    station_id      BIGINT NOT NULL REFERENCES charging_station(id) ON DELETE CASCADE,
    charger_index   INTEGER NOT NULL,          -- 스테이션 내 0-based 인덱스
    charger_type    VARCHAR(10),               -- "fast" / "slow"
    rated_power_kw  DOUBLE PRECISION,          -- 정격 출력 (kW)
    UNIQUE (station_id, charger_index)
);

-- ============================================================
-- 3. 스테이션 제약 조건
-- ============================================================
CREATE TABLE constraints (
    id                   BIGSERIAL PRIMARY KEY,
    station_id           BIGINT NOT NULL UNIQUE REFERENCES charging_station(id) ON DELETE CASCADE,
    soc_min              DOUBLE PRECISION DEFAULT 0.1,
    soc_max              DOUBLE PRECISION DEFAULT 0.9,
    ess_max_charge       DOUBLE PRECISION DEFAULT 50.0,
    ess_max_discharge    DOUBLE PRECISION DEFAULT 50.0,
    ess_capacity_kwh     DOUBLE PRECISION DEFAULT 100.0,
    grid_import_limit_kw DOUBLE PRECISION DEFAULT 120.0,
    grid_export_limit_kw DOUBLE PRECISION DEFAULT 120.0
);

-- ============================================================
-- 4. 스테이션 실시간 상태 (MQTT 수신마다 덮어씀)
-- ============================================================
CREATE TABLE station_state (
    id           BIGSERIAL PRIMARY KEY,
    station_id   BIGINT NOT NULL UNIQUE REFERENCES charging_station(id) ON DELETE CASCADE,
    soc          DOUBLE PRECISION,
    demand_count INTEGER DEFAULT 0,
    updated_at   TIMESTAMP
);

-- ============================================================
-- 5. 전력 계측 (station_state 1:1)
-- ============================================================
CREATE TABLE power_metrics (
    id               BIGSERIAL PRIMARY KEY,
    station_state_id BIGINT NOT NULL UNIQUE REFERENCES station_state(id) ON DELETE CASCADE,
    p_pv             DOUBLE PRECISION,
    p_load           DOUBLE PRECISION,
    p_ess            DOUBLE PRECISION,
    p_grid           DOUBLE PRECISION,
    p_tr             DOUBLE PRECISION
);

-- ============================================================
-- 6. 충전기별 실시간 상태 (MQTT 수신마다 덮어씀)
--    mode: FAST / SLOW (현재 충전 모드, 동적 변경됨)
-- ============================================================
CREATE TABLE charger_state (
    id               BIGSERIAL PRIMARY KEY,
    station_state_id BIGINT NOT NULL REFERENCES station_state(id) ON DELETE CASCADE,
    charger_id       BIGINT NOT NULL REFERENCES charger(id),
    mode             VARCHAR(10),
    power_demand     DOUBLE PRECISION DEFAULT 0.0,
    is_active        BOOLEAN DEFAULT FALSE,
    UNIQUE (station_state_id, charger_id)
);

-- ============================================================
-- 7. 시간별 스냅샷 (매 정각 저장, 수요 이력 겸용)
--    AI 요청 시 최근 7일치만 사용, 초과분은 자동 삭제
-- ============================================================
CREATE TABLE hourly_snapshot (
    id           BIGSERIAL PRIMARY KEY,
    station_id   BIGINT NOT NULL REFERENCES charging_station(id),
    recorded_at  TIMESTAMP NOT NULL,
    soc          DOUBLE PRECISION,
    demand_count INTEGER,
    p_pv         DOUBLE PRECISION,
    p_load       DOUBLE PRECISION,
    p_ess        DOUBLE PRECISION,
    p_grid       DOUBLE PRECISION,
    p_tr         DOUBLE PRECISION,
    capacity_wh  DOUBLE PRECISION
);
CREATE INDEX idx_snapshot_station_time ON hourly_snapshot(station_id, recorded_at);
CREATE INDEX idx_snapshot_recorded_at  ON hourly_snapshot(recorded_at);

-- ============================================================
-- 8. AI 스케줄 작업 (1회 응답 = 1 ScheduleJob)
--    request_id / schedule_target_date : AI 응답의 최상위 필드
--    response_at : AI 응답 timestamp (timezone 포함)
--    model_algorithm / model_version   : 사용된 AI 모델 정보
-- ============================================================
CREATE TABLE schedule_job (
    id                   BIGSERIAL PRIMARY KEY,
    request_id           VARCHAR(255) UNIQUE,
    schedule_target_date DATE NOT NULL,
    schedule_mode        VARCHAR(50),           -- "day-ahead" 등
    model_algorithm      VARCHAR(50),           -- "SAC" 등
    model_version        VARCHAR(100),
    response_at          TIMESTAMPTZ,           -- AI 응답의 timestamp 필드
    created_at           TIMESTAMP,             -- 스프링 서버 수신 시각
    completed_at         TIMESTAMP,
    status               VARCHAR(50),           -- "COMPLETED" / "FAILED"
    error_message        TEXT
);
CREATE INDEX idx_job_target_date ON schedule_job(schedule_target_date);

-- ============================================================
-- 9. 스케줄 최적화 지표 (ScheduleJob 1:1)
--    AI 응답 metrics 블록 전체를 저장
-- ============================================================
CREATE TABLE schedule_metrics (
    id                              BIGSERIAL PRIMARY KEY,
    schedule_job_id                 BIGINT NOT NULL UNIQUE REFERENCES schedule_job(id) ON DELETE CASCADE,
    grid_only_cost_krw              DOUBLE PRECISION,
    sac_cost_with_transfer_krw      DOUBLE PRECISION,
    cost_reduction_krw              DOUBLE PRECISION,
    cost_reduction_pct              DOUBLE PRECISION,
    total_demand_kwh                DOUBLE PRECISION,
    total_pv_kwh                    DOUBLE PRECISION,
    total_grid_usage_kwh            DOUBLE PRECISION,
    total_self_supply_kwh           DOUBLE PRECISION,
    total_pv_lost_kwh               DOUBLE PRECISION,
    total_transfer_out_kwh          DOUBLE PRECISION,
    total_transfer_in_kwh           DOUBLE PRECISION,
    total_transfer_loss_kwh         DOUBLE PRECISION,
    max_cluster_grid_usage_kwh_per_hour DOUBLE PRECISION,
    peak_violation_slots            INTEGER,
    soc_min                         DOUBLE PRECISION,
    soc_max                         DOUBLE PRECISION,
    total_reward_before_transfer    DOUBLE PRECISION,
    device                          VARCHAR(50),
    torch_cuda_available            BOOLEAN,
    cuda_device_name                VARCHAR(100),
    transfer_postprocess_enabled    BOOLEAN
);

-- ============================================================
-- 10. 스테이션별 스케줄 결과
-- ============================================================
CREATE TABLE schedule_result (
    id              BIGSERIAL PRIMARY KEY,
    schedule_job_id BIGINT NOT NULL REFERENCES schedule_job(id) ON DELETE CASCADE,
    station_id      BIGINT NOT NULL REFERENCES charging_station(id),
    station_name    VARCHAR(100)
);

-- ============================================================
-- 11. 시간별 ESS/Grid 운전 계획 (ScheduleResult당 24개)
--    ess_power_kw        : 충방전 절대값 (kW)
--    ess_power_signed_kw : 양수=충전, 음수=방전 (kW)
--    ess_energy_kwh      : 1시간 환산 에너지 (kWh)
-- ============================================================
CREATE TABLE hourly_plan (
    id                      BIGSERIAL PRIMARY KEY,
    schedule_result_id      BIGINT NOT NULL REFERENCES schedule_result(id) ON DELETE CASCADE,
    hour                    INTEGER NOT NULL,
    ess_mode                VARCHAR(20),        -- "charge" / "discharge" / "idle"
    ess_power_kw            DOUBLE PRECISION,
    ess_power_signed_kw     DOUBLE PRECISION,
    ess_energy_kwh          DOUBLE PRECISION,
    grid_usage_kw           DOUBLE PRECISION,
    grid_usage_kwh          DOUBLE PRECISION,
    pv_generation_pred_kwh  DOUBLE PRECISION,
    load_pred_kwh           DOUBLE PRECISION,
    pv_priority             DOUBLE PRECISION,
    expected_soc            DOUBLE PRECISION
);

-- ============================================================
-- 12. 스테이션 간 전력 이송 계획
--    target_station_id : charging_station.id (station_index로 조회 후 저장)
-- ============================================================
CREATE TABLE transfer (
    id                  BIGSERIAL PRIMARY KEY,
    hourly_plan_id      BIGINT NOT NULL REFERENCES hourly_plan(id) ON DELETE CASCADE,
    target_station_id   BIGINT NOT NULL REFERENCES charging_station(id),
    transfer_power_kw   DOUBLE PRECISION,
    received_power_kw   DOUBLE PRECISION,
    loss_power_kw       DOUBLE PRECISION,
    transfer_energy_kwh DOUBLE PRECISION,
    received_energy_kwh DOUBLE PRECISION,
    loss_energy_kwh     DOUBLE PRECISION
);

-- ============================================================
-- 13. 클러스터 시간별 예측 (PV + 클러스터 수요, ScheduleJob당 24개)
--    forecast_results.pv_day_ahead_forecast 와
--    forecast_results.demand_cluster_forecast 를 통합 저장
-- ============================================================
CREATE TABLE cluster_forecast (
    id                           BIGSERIAL PRIMARY KEY,
    schedule_job_id              BIGINT NOT NULL REFERENCES schedule_job(id) ON DELETE CASCADE,
    hour                         INTEGER NOT NULL,
    predicted_pv_kwh_per_station DOUBLE PRECISION,
    predicted_cluster_pv_kwh     DOUBLE PRECISION,
    predicted_cluster_demand_kwh DOUBLE PRECISION,
    UNIQUE (schedule_job_id, hour)
);

-- ============================================================
-- 14. 스테이션별 수요 예측 (ScheduleJob × 스테이션당 24개)
--    forecast_results.demand_day_ahead_forecast
--    station_index : charging_station.station_index (0-based)
-- ============================================================
CREATE TABLE station_demand_forecast (
    id                   BIGSERIAL PRIMARY KEY,
    schedule_job_id      BIGINT NOT NULL REFERENCES schedule_job(id) ON DELETE CASCADE,
    station_index        INTEGER NOT NULL,
    hour                 INTEGER NOT NULL,
    predicted_demand_kwh DOUBLE PRECISION,
    UNIQUE (schedule_job_id, station_index, hour)
);

-- ============================================================
-- 시드 데이터  (sample_ai_server_request.json 기준)
-- ============================================================

-- ── 충전 스테이션 5개 ─────────────────────────────────────────
-- station_index = AI/MQTT 0-based 인덱스
-- station_index=0 (LH강남힐스테이트) : 물리 스테이션 (IoT 실제 연결)
-- station_index=1~4                  : 시뮬레이션 스테이션
INSERT INTO charging_station (name, location, station_index, ess_capacity_kwh, is_active) VALUES
    ('LH강남힐스테이트',     '서울 강남구', 0, 100.0, TRUE),
    ('LH서울지사',           '서울 강남구', 1, 100.0, TRUE),
    ('강남구청 공영주차장',   '서울 강남구', 2, 100.0, TRUE),
    ('강남한양수자인',        '서울 강남구', 3, 100.0, TRUE),
    ('도곡렉슬 아파트',       '서울 강남구', 4, 100.0, TRUE);

-- ── 충전기 (스테이션당 5개, charger_index 0~4) ────────────────
-- 패턴: fast(50kW) · slow(7kW) · slow(7kW) · fast(50kW) · slow(7kW)
INSERT INTO charger (station_id, charger_index, charger_type, rated_power_kw) VALUES
    -- LH강남힐스테이트 (id=1)
    (1, 0, 'fast', 50.0),
    (1, 1, 'slow',  7.0),
    (1, 2, 'slow',  7.0),
    (1, 3, 'fast', 50.0),
    (1, 4, 'slow',  7.0),
    -- LH서울지사 (id=2)
    (2, 0, 'fast', 50.0),
    (2, 1, 'slow',  7.0),
    (2, 2, 'slow',  7.0),
    (2, 3, 'fast', 50.0),
    (2, 4, 'slow',  7.0),
    -- 강남구청 공영주차장 (id=3)
    (3, 0, 'fast', 50.0),
    (3, 1, 'slow',  7.0),
    (3, 2, 'slow',  7.0),
    (3, 3, 'fast', 50.0),
    (3, 4, 'slow',  7.0),
    -- 강남한양수자인 (id=4)
    (4, 0, 'fast', 50.0),
    (4, 1, 'slow',  7.0),
    (4, 2, 'slow',  7.0),
    (4, 3, 'fast', 50.0),
    (4, 4, 'slow',  7.0),
    -- 도곡렉슬 아파트 (id=5)
    (5, 0, 'fast', 50.0),
    (5, 1, 'slow',  7.0),
    (5, 2, 'slow',  7.0),
    (5, 3, 'fast', 50.0),
    (5, 4, 'slow',  7.0);

-- ── 제약 조건 (ess_constraints + grid_constraints 기준) ────────
-- soc_min=0.1, soc_max=0.9  (ess_constraints.ess_min/max_soc)
-- ess_max_charge/discharge=50.0  (ess_constraints.ess_max_charge/discharge_kw)
-- grid_import/export_limit=120.0 (grid_constraints.station_grid_limit_kw)
INSERT INTO constraints (station_id, soc_min, soc_max, ess_max_charge, ess_max_discharge, ess_capacity_kwh, grid_import_limit_kw, grid_export_limit_kw) VALUES
    (1, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0),
    (2, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0),
    (3, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0),
    (4, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0),
    (5, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0);

-- ── 스테이션 실시간 상태 초기값 (station_state) ────────────────
-- SOC 0.5로 초기화, MQTT 수신 시 덮어씀
INSERT INTO station_state (station_id, soc, demand_count, updated_at)
SELECT id, 0.5, 0, NOW() FROM charging_station ORDER BY id;

-- ── 전력 계측 초기값 (power_metrics) ─────────────────────────────
-- station_state 1:1, 모두 0으로 초기화
INSERT INTO power_metrics (station_state_id, p_pv, p_load, p_ess, p_grid, p_tr)
SELECT id, 0.0, 0.0, 0.0, 0.0, 0.0 FROM station_state ORDER BY id;

-- ── 충전기 실시간 상태 초기값 (charger_state) ─────────────────────
-- 스테이션당 5개 충전기, 전부 비활성 상태로 초기화
INSERT INTO charger_state (station_state_id, charger_id, mode, power_demand, is_active)
SELECT ss.id, c.id, NULL, 0.0, FALSE
FROM station_state ss
JOIN charger c ON c.station_id = ss.station_id
ORDER BY ss.id, c.charger_index;

-- ── 과거 7일 시간별 스냅샷 (hourly_snapshot) ──────────────────────
-- p_load / p_pv / p_grid 단위: W (서비스에서 /1000 → kW 변환)
-- 시간대별 부하 패턴
--   08~20시(피크): p_load 10~20 kW, p_grid 8~16 kW
--   나머지(경부하): p_load  3~8 kW, p_grid  3~8 kW
-- PV 발전: station_index=0(물리) 08~17시에만 1~4 kW, 나머지 스테이션은 0
-- recorded_at은 KST LocalDateTime으로 저장 (서비스가 atZone(KST)로 해석)
INSERT INTO hourly_snapshot (station_id, recorded_at, soc, demand_count, p_pv, p_load, p_ess, p_grid, p_tr, capacity_wh)
SELECT
    cs.id,
    gs AS recorded_at,
    ROUND((0.4 + RANDOM() * 0.2)::numeric, 3)   AS soc,
    0                                             AS demand_count,
    CASE
        WHEN cs.station_index = 0 AND EXTRACT(HOUR FROM gs) BETWEEN 8 AND 17
        THEN ROUND((1000 + RANDOM() * 3000)::numeric, 1)
        ELSE 0.0
    END                                           AS p_pv,
    CASE
        WHEN EXTRACT(HOUR FROM gs) BETWEEN 8 AND 20
        THEN ROUND((10000 + RANDOM() * 10000)::numeric, 1)
        ELSE ROUND((3000  + RANDOM() * 5000)::numeric,  1)
    END                                           AS p_load,
    0.0                                           AS p_ess,
    CASE
        WHEN EXTRACT(HOUR FROM gs) BETWEEN 8 AND 20
        THEN ROUND((8000  + RANDOM() * 8000)::numeric, 1)
        ELSE ROUND((3000  + RANDOM() * 5000)::numeric, 1)
    END                                           AS p_grid,
    0.0                                           AS p_tr,
    100000.0                                      AS capacity_wh
FROM charging_station cs,
     generate_series(
         date_trunc('hour', (NOW() AT TIME ZONE 'Asia/Seoul'))::timestamp - INTERVAL '7 days',
         date_trunc('hour', (NOW() AT TIME ZONE 'Asia/Seoul'))::timestamp - INTERVAL '1 hour',
         INTERVAL '1 hour'
     ) AS gs
ORDER BY cs.id, gs;
