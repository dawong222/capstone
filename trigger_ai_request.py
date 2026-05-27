"""
May 21 22:10 시뮬레이션 타임스탬프로 MQTT 메시지를 전송하여
Spring Boot AI 요청 트리거를 강제 실행합니다.
"""
import paho.mqtt.client as mqtt
import json, time

BROKER = "13.124.116.74"
PORT   = 1883
TOPIC  = "smartgrid/telemetry"

TIMESTAMP = "2026-05-21T22:10:00+09:00"
NUM_STATIONS = 5

stations = []
for i in range(NUM_STATIONS):
    stations.append({
        "header": {
            "station_id": i,
            "is_physical": False,
            "timestamp": TIMESTAMP,
            "day_idx": 7,
            "step": 22
        },
        "payload": {
            "charger_status": [{"charger_id": c, "has_demand": False} for c in range(5)],
            "power_metrics_w": {"p_pv": 0.0, "p_load": 0.0, "p_ess": 0.0, "p_grid": 0.0, "p_tr": 0.0},
            "state_of_charge": {"mode": "idle", "soc": 0.5, "capacity_wh": 998.0}
        },
        "status": {"is_active": True, "error_code": 0}
    })

payload = {"stations": stations, "weather": {}}

def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        print(f"연결 성공")
        client.publish(TOPIC, json.dumps(payload))
        print(f"전송 완료: timestamp={TIMESTAMP}")
    else:
        print(f"연결 실패 rc={rc}")

try:
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="trigger_ai")
except AttributeError:
    client = mqtt.Client(client_id="trigger_ai")

client.on_connect = on_connect
client.connect(BROKER, PORT, 60)
client.loop_start()
time.sleep(3)
client.loop_stop()
client.disconnect()
