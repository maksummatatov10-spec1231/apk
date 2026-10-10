#!/usr/bin/env python3
"""Лаборатория решателя: та же математика, что в приложении, в браузере.

Запуск (из calc-app/):  scripts/build-cli.sh && python3 lab/server.py [порт]
Сервер держит одну JVM с SolverCli (тот же код решателя, что в APK) и отдаёт:
  GET  /                — страница с полем ввода и решениями
  GET  /api/catalog     — примеры из приложения (раздел «Уравнения» и «Примеры»)
  POST /api/solve       — {"query": "..."} -> {"heading", "answers", "steps", "error", ...}
"""
import json
import os
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
CALC = HERE.parent
CLASSES = CALC / "build" / "cli" / "classes"
JAVA = os.environ.get("JAVA", str(Path.home() / "android-toolchain" / "bin" / "java"))


class Solver:
    """Одна JVM на всё время работы сервера; запросы сериализованы блокировкой."""

    def __init__(self):
        self.lock = threading.Lock()
        self.proc = None

    def _start(self):
        self.proc = subprocess.Popen(
            [JAVA, "-Dfile.encoding=UTF-8", "-cp", str(CLASSES), "com.example.calcpro.SolverCli"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding="utf-8", bufsize=1,
        )

    def solve(self, query: str) -> dict:
        # Перевод строки в «;» — решатель трактует их одинаково (система уравнений).
        line = " ; ".join(part.strip() for part in query.replace("\r", "").split("\n") if part.strip())
        if not line:
            return {"error": "Введите выражение или уравнение"}
        with self.lock:
            if self.proc is None or self.proc.poll() is not None:
                self._start()
            self.proc.stdin.write(line + "\n")
            self.proc.stdin.flush()
            reply = self.proc.stdout.readline()
        if not reply:
            return {"error": "Решатель не ответил"}
        return json.loads(reply)

    def catalog(self) -> list:
        out = subprocess.run([JAVA, "-Dfile.encoding=UTF-8", "-cp", str(CLASSES),
                              "com.example.calcpro.SolverCli", "--catalog"],
                             capture_output=True, text=True, encoding="utf-8", check=True)
        return json.loads(out.stdout)


SOLVER = Solver()


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            self._send(200, (HERE / "index.html").read_bytes(), "text/html; charset=utf-8")
        elif self.path == "/api/catalog":
            self._send(200, json.dumps(SOLVER.catalog(), ensure_ascii=False))
        else:
            self._send(404, json.dumps({"error": "not found"}))

    def do_POST(self):
        if self.path != "/api/solve":
            self._send(404, json.dumps({"error": "not found"}))
            return
        length = int(self.headers.get("Content-Length", "0"))
        try:
            body = json.loads(self.rfile.read(length) or b"{}")
            result = SOLVER.solve(str(body.get("query", "")))
        except Exception as e:  # защита сервера от неверного запроса
            result = {"error": f"Ошибка запроса: {e}"}
        self._send(200, json.dumps(result, ensure_ascii=False))

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    if not CLASSES.exists():
        sys.exit("Нет build/cli/classes. Сначала: scripts/build-cli.sh")
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"Лаборатория решателя: http://0.0.0.0:{port}", flush=True)
    server.serve_forever()
