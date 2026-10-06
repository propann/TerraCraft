#!/usr/bin/env python3
import json
import mimetypes
import urllib.parse
import urllib.request
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

WEB_DIR = Path(__file__).resolve().parent
DATA_DIR = WEB_DIR.parent / "data"
SELECTION_FILE = DATA_DIR / "selection.json"


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_DIR), **kwargs)

    def do_POST(self):
        if self.path != "/api/selection":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length))
            latitude = float(payload["latitude"])
            longitude = float(payload["longitude"])
            if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
                raise ValueError("coordinates outside valid range")
            query = urllib.parse.urlencode({
                "latitude": latitude,
                "longitude": longitude,
                "current": "temperature_2m,precipitation,weather_code,wind_speed_10m",
                "timezone": "auto",
            })
            with urllib.request.urlopen(
                f"https://api.open-meteo.com/v1/forecast?{query}", timeout=15
            ) as response:
                weather = json.load(response)
            result = {
                "latitude": latitude,
                "longitude": longitude,
                "weather": weather,
            }
            DATA_DIR.mkdir(parents=True, exist_ok=True)
            SELECTION_FILE.write_text(json.dumps(result, indent=2), encoding="utf-8")
            self.send_json(200, result)
        except Exception as exc:
            self.send_json(502, {"error": str(exc)})

    def send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    print("Sélection de carte : http://localhost:8080")
    ThreadingHTTPServer(("127.0.0.1", 8080), Handler).serve_forever()
