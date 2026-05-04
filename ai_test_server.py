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


@app.post("/ai/control")
async def receive_request(request: Request):
    global last_request
    body = await request.json()
    last_request = body

    request_id  = body.get("request_id", "unknown")
    target_date = body.get("schedule_target_date", "unknown")

    demand_count  = len(body.get("demand_past_demand_hourly", []))
    d_weather     = len(body.get("demand_past_weather_hourly", []))
    d_forecast    = len(body.get("demand_forecast_short_term_hourly", []))
    pv_gen        = len(body.get("pv_past_generation_hourly", []))
    pv_weather    = len(body.get("pv_past_weather_hourly", []))
    pv_forecast   = len(body.get("pv_forecast_short_term_hourly", []))
    stations      = len(body.get("station_current_states", []))
    tou           = len(body.get("tou_price_hourly", []))

    log.info("=" * 60)
    log.info(f"[수신] request_id      : {request_id}")
    log.info(f"[수신] target_date     : {target_date}")
    log.info(f"[수신] demand_past_demand_hourly        : {demand_count}건")
    log.info(f"[수신] demand_past_weather_hourly       : {d_weather}건")
    log.info(f"[수신] demand_forecast_short_term_hourly: {d_forecast}건")
    log.info(f"[수신] pv_past_generation_hourly        : {pv_gen}건")
    log.info(f"[수신] pv_past_weather_hourly           : {pv_weather}건")
    log.info(f"[수신] pv_forecast_short_term_hourly    : {pv_forecast}건")
    log.info(f"[수신] station_current_states           : {stations}건")
    log.info(f"[수신] tou_price_hourly                 : {tou}건")
    log.info("=" * 60)

    filename = f"received_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(body, f, ensure_ascii=False, indent=2)
    log.info(f"[저장] {filename} 에 전체 요청 저장 완료")

    return {"status": "ok", "request_id": request_id, "received_at": datetime.now().isoformat()}


@app.get("/ai/request")
async def get_last_request():
    if not last_request:
        return JSONResponse(status_code=404, content={"message": "아직 수신된 요청이 없습니다."})
    return JSONResponse(content=last_request)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
