import paho.mqtt.client as mqtt
import json, datetime, time

BROKER = "13.124.116.74"
PORT   = 1883
TOPIC  = "smartgrid/telemetry"

received = []

def on_connect(client, userdata, flags, rc):
    print(f"[{datetime.datetime.now():%H:%M:%S}] 브로커 연결 rc={rc}")
    client.subscribe(TOPIC)
    client.subscribe("simulate/telemetry")

def on_message(client, userdata, msg):
    try:
        data = json.loads(msg.payload)
        stations = data.get("stations", [])
        if not stations:
            return
        ts = stations[0].get("header", {}).get("timestamp", "")
        day_idx = stations[0].get("header", {}).get("day_idx", "?")
        step    = stations[0].get("header", {}).get("step", "?")
        print(f"[{datetime.datetime.now():%H:%M:%S}] topic={msg.topic} | sim_ts={ts} | day_idx={day_idx} step={step}")
        received.append(ts)
    except Exception as e:
        print(f"파싱 오류: {e}")

client = mqtt.Client(client_id="monitor_check")
client.on_connect = on_connect
client.on_message = on_message

print(f"[{datetime.datetime.now():%H:%M:%S}] {BROKER}:{PORT} 연결 시도...")
client.connect(BROKER, PORT, keepalive=60)

start = time.time()
client.loop_start()
time.sleep(15)
client.loop_stop()
client.disconnect()

print(f"\n수신한 메시지 {len(received)}개")
if received:
    print(f"타임스탬프 범위: {min(received)} ~ {max(received)}")
