import atexit
import logging
import os
import secrets
import threading
import webbrowser
import subprocess
import sys
from pathlib import Path

import requests
from flask import Flask, jsonify, request, send_from_directory
from werkzeug.serving import make_server

from casting import CastManager
from mykadri import HOME, EXAMPLE, fetch_page, page_url, parse_movie, parse_catalog

PORT = int(os.environ.get("KADRI_PORT", "8765"))
BASE = Path(__file__).resolve().parent
app = Flask(__name__, static_folder=str(BASE / "static"), static_url_path="")
token = secrets.token_urlsafe(32)
manager = CastManager()
movies = {}
operation = threading.Lock()
atexit.register(manager.close)


@app.before_request
def local_only():
    if request.host not in {f"localhost:{PORT}", f"127.0.0.1:{PORT}"}:
        return jsonify(error="Local access only"), 403
    if request.method == "POST":
        if request.headers.get("Origin") not in {f"http://localhost:{PORT}", f"http://127.0.0.1:{PORT}"}:
            return jsonify(error="Invalid origin"), 403
        if not secrets.compare_digest(request.headers.get("X-Kadri-Token", ""), token):
            return jsonify(error="Invalid session"), 403


@app.after_request
def response_headers(response):
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Content-Security-Policy"] = "default-src 'self'; img-src 'self' https: data:; style-src 'self'; script-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
    if request.path.startswith("/api/"):
        response.headers["Cache-Control"] = "no-store"
    return response


@app.errorhandler(Exception)
def errors(error):
    if isinstance(error, requests.RequestException):
        return jsonify(error="MyKadri-ს ვერ დავუკავშირდი. შეამოწმე ინტერნეტი და სცადე ხელახლა."), 502
    if isinstance(error, (ValueError, TypeError, KeyError)):
        return jsonify(error=str(error)), 400
    if hasattr(error, "code") and isinstance(error.code, int):
        return jsonify(error=error.description), error.code
    logging.exception("Request failed")
    return jsonify(error="მოქმედება ვერ დასრულდა. გადაამოწმე TV-ის კავშირი და სცადე ხელახლა."), 502


@app.get("/")
def index():
    return send_from_directory(BASE / "static", "index.html")


@app.get("/api/bootstrap")
def bootstrap():
    return jsonify(token=token, example=EXAMPLE, name="Kadri TV")


@app.get("/api/catalog")
def catalog():
    term = request.args.get("q", "").strip()[:150]
    params = {"do": "search", "subaction": "search", "story": term} if term else None
    html, _ = fetch_page(HOME, params)
    return jsonify(items=parse_catalog(html))


@app.post("/api/resolve")
def resolve():
    html, url = fetch_page(page_url(request.get_json()["url"]))
    movie = parse_movie(html, url)
    movies[url] = movie
    return jsonify(movie)


@app.get("/api/devices")
def devices():
    return jsonify(manager.devices())


@app.get("/api/status")
def status():
    return jsonify(manager.status())


@app.post("/api/connect")
def connect():
    data = request.get_json()
    with operation:
        manager.connect(data.get("id"), data.get("host"))
        return jsonify(manager.status())


@app.post("/api/cast")
def cast():
    data = request.get_json()
    movie = movies.get(data.get("url"))
    if not movie:
        raise ValueError("ჯერ გახსენი ფილმი.")
    source = next((s for s in movie["sources"] if s["id"] == str(data.get("sourceId"))), None)
    if not source:
        raise ValueError("აირჩიე სერია ან ვიდეონაკადი.")
    with operation:
        return jsonify(manager.play(movie, source))


@app.post("/api/control")
def control():
    data = request.get_json()
    with operation:
        return jsonify(manager.command(data.get("action"), data.get("value")))


if __name__ == "__main__":
    server = make_server("127.0.0.1", PORT, app, threaded=True)
    if os.environ.get("KADRI_NO_BROWSER") != "1":
        def open_app():
            url = f"http://localhost:{PORT}"
            if sys.platform == "darwin":
                subprocess.Popen(["open", "-a", "Google Chrome", url])
            else:
                webbrowser.open(url)
        threading.Timer(0.5, open_app).start()
    print(f"Kadri TV → http://localhost:{PORT}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
