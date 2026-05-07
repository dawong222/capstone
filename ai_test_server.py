import json
import logging
from datetime import datetime
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
import uvicorn

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
log = logging.getLogger(__name__)

app = FastAPI()

last_request: dict = {}


def _make_hourly_plan(hour: int) -> dict:
    """시간대별 ESS 제어 결정 생성 (테스트용 더미 스케줄)"""
    if 0 <= hour < 8 or 22 <= hour < 24:
        # 심야 저가 시간대: ESS 충전
        return {"hour": hour, "ess_mode": "CHARGE", "ess_power": 30.0,
                "grid_usage": 40.0, "pv_priority": 0.3, "transfer": []}
    elif 11 <= hour < 18:
        # 피크 시간대: ESS 방전
        return {"hour": hour, "ess_mode": "DISCHARGE", "ess_power": -30.0,
                "grid_usage": 5.0, "pv_priority": 0.9, "transfer": []}
    else:
        # 그 외: 대기
        return {"hour": hour, "ess_mode": "IDLE", "ess_power": 0.0,
                "grid_usage": 15.0, "pv_priority": 0.6, "transfer": []}


@app.post("/ai/control")
async def receive_request(request: Request):
    global last_request
    body = await request.json()
    last_request = body

    request_id  = body.get("request_id", "unknown")
    target_date = body.get("schedule_target_date", "unknown")

    stations_data = body.get("stations", [])
    tou           = len(body.get("tou_price_hourly", []))

    log.info("=" * 60)
    log.info(f"[수신] request_id      : {request_id}")
    log.info(f"[수신] target_date     : {target_date}")
    log.info(f"[수신] station_count   : {len(stations_data)}개")
    log.info(f"[수신] tou_price_hourly: {tou}건")
    log.info("=" * 60)

    filename = f"received_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(body, f, ensure_ascii=False, indent=2)
    log.info(f"[저장] {filename} 에 전체 요청 저장 완료")

    # 수신된 스테이션 ID 목록 (없으면 0~4 기본값)
    station_ids = [s.get("station_id", i) for i, s in enumerate(stations_data)]
    if not station_ids:
        station_ids = list(range(5))

    schedule = [
        {
            "station_id": sid,
            "hourly_plan": [_make_hourly_plan(h) for h in range(24)]
        }
        for sid in station_ids
    ]

    return {
        "request_id": request_id,
        "timestamp": datetime.now().isoformat(),
        "status": {
            "is_success": True,
            "error_code": 0,
            "message": "SUCCESS"
        },
        "station_day_ahead_schedule": schedule
    }


@app.get("/ai/request")
async def get_last_request():
    if not last_request:
        return JSONResponse(status_code=404, content={"message": "아직 수신된 요청이 없습니다."})
    return JSONResponse(content=last_request)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
