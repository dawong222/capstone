-- ============================================================
-- DB 초기화 스크립트 (capstonedb / PostgreSQL)
-- 실행 전 주의: 기존 데이터 전부 삭제됩니다.
-- 실행: psql -h <host> -U capstone -d capstonedb -f reset_schema.sql
-- ============================================================

-- 기존 테이블 삭제 (FK 순서 역순)
DROP TABLE IF EXISTS transfer CASCADE;
DROP TABLE IF EXISTS hourly_plan CASCADE;
DROP TABLE IF EXISTS schedule_result CASCADE;
DROP TABLE IF EXISTS cluster_state CASCADE;
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
-- ============================================================
CREATE TABLE charger (
    id            BIGSERIAL PRIMARY KEY,
    station_id    BIGINT NOT NULL REFERENCES charging_station(id) ON DELETE CASCADE,
    charger_index INTEGER NOT NULL,            -- 스테이션 내 0-based 인덱스
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
-- 8. AI 스케줄 작업 (1회 요청 = 1 ScheduleJob)
-- ============================================================
CREATE TABLE schedule_job (
    id                   BIGSERIAL PRIMARY KEY,
    request_id           VARCHAR(255) UNIQUE,
    schedule_target_date DATE NOT NULL,
    created_at           TIMESTAMP,
    completed_at         TIMESTAMP,
    status               VARCHAR(50),
    error_message        TEXT
);
CREATE INDEX idx_job_target_date ON schedule_job(schedule_target_date);

-- ============================================================
-- 9. 클러스터 시간대별 조건 (ScheduleJob당 24개)
-- ============================================================
CREATE TABLE cluster_state (
    id               BIGSERIAL PRIMARY KEY,
    schedule_job_id  BIGINT NOT NULL REFERENCES schedule_job(id) ON DELETE CASCADE,
    time_index       INTEGER NOT NULL,
    tou_level        VARCHAR(20),
    tou_price        DOUBLE PRECISION,
    grid_limit       DOUBLE PRECISION,
    transfer_enabled BOOLEAN DEFAULT TRUE,
    day_of_week      INTEGER
);

-- ============================================================
-- 10. 스테이션별 스케줄 결과
-- ============================================================
CREATE TABLE schedule_result (
    id              BIGSERIAL PRIMARY KEY,
    schedule_job_id BIGINT NOT NULL REFERENCES schedule_job(id) ON DELETE CASCADE,
    station_id      BIGINT NOT NULL REFERENCES charging_station(id)
);

-- ============================================================
-- 11. 시간별 ESS/Grid 운전 계획 (ScheduleResult당 24개)
-- ============================================================
CREATE TABLE hourly_plan (
    id                 BIGSERIAL PRIMARY KEY,
    schedule_result_id BIGINT NOT NULL REFERENCES schedule_result(id) ON DELETE CASCADE,
    hour               INTEGER NOT NULL,
    ess_mode           VARCHAR(20),
    ess_power          DOUBLE PRECISION,
    grid_usage         DOUBLE PRECISION,
    pv_priority        DOUBLE PRECISION
);

-- ============================================================
-- 12. 스테이션 간 전력 이송 계획
-- ============================================================
CREATE TABLE transfer (
    id                BIGSERIAL PRIMARY KEY,
    hourly_plan_id    BIGINT NOT NULL REFERENCES hourly_plan(id) ON DELETE CASCADE,
    target_station_id BIGINT NOT NULL REFERENCES charging_station(id),
    power             DOUBLE PRECISION
);

-- ============================================================
-- 시드 데이터
-- ============================================================

-- 충전 스테이션 5개
INSERT INTO charging_station (name, location, station_index, ess_capacity_kwh, is_active) VALUES
    ('Station-A', 'Zone-1', 0, 100.0, TRUE),
    ('Station-B', 'Zone-2', 1, 100.0, TRUE),
    ('Station-C', 'Zone-3', 2, 100.0, TRUE),
    ('Station-D', 'Zone-4', 3, 100.0, TRUE),
    ('Station-E', 'Zone-5', 4, 100.0, TRUE);

-- 충전기 (스테이션당 5개, charger_index 0~4)
INSERT INTO charger (station_id, charger_index) VALUES
    (1,0),(1,1),(1,2),(1,3),(1,4),
    (2,0),(2,1),(2,2),(2,3),(2,4),
    (3,0),(3,1),(3,2),(3,3),(3,4),
    (4,0),(4,1),(4,2),(4,3),(4,4),
    (5,0),(5,1),(5,2),(5,3),(5,4);

-- 제약 조건 (스테이션별)
INSERT INTO constraints (station_id, soc_min, soc_max, ess_max_charge, ess_max_discharge, ess_capacity_kwh, grid_import_limit_kw, grid_export_limit_kw) VALUES
    (1, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0),
    (2, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0),
    (3, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0),
    (4, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0),
    (5, 0.1, 0.9, 50.0, 50.0, 100.0, 120.0, 120.0);
