import ipaddress
import math
import threading
import time
from uuid import UUID, uuid5, NAMESPACE_DNS

import pychromecast
from zeroconf import Zeroconf


class CastManager:
    def __init__(self):
        self.lock = threading.RLock()
        self.browser = None
        self.zconf = None
        self.error = ""
        self.cast = None
        self.active = None

    def start(self):
        with self.lock:
            if self.browser:
                return
            try:
                self.zconf = Zeroconf()
                self.browser = pychromecast.CastBrowser(pychromecast.SimpleCastListener(), self.zconf)
                self.browser.start_discovery()
            except Exception:
                self.error = "TV-ის ავტომატური ძებნა ვერ ჩაირთო. შეგიძლია IP მისამართით დაკავშირება."
                if self.zconf:
                    self.zconf.close()
                self.browser = None

    def devices(self):
        self.start()
        devices = []
        if self.browser:
            for info in list(self.browser.devices.copy().values()):
                if info.cast_type not in ("audio", "group"):
                    devices.append({"id": str(info.uuid), "name": info.friendly_name or info.host,
                                    "host": info.host, "model": info.model_name or "Google Cast"})
        return {"devices": sorted(devices, key=lambda d: d["name"]), "error": self.error}

    def connect(self, device_id=None, host=None):
        if host:
            addr = ipaddress.ip_address(host)
            networks = ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")
            if not any(addr in ipaddress.ip_network(n) for n in networks):
                raise ValueError("მიუთითე TV-ის ლოკალური IPv4 მისამართი, მაგალითად 192.168.1.20.")
            cc = pychromecast.get_chromecast_from_host((str(addr), 8009, uuid5(NAMESPACE_DNS, str(addr)), "Sony Bravia", "Sony"), tries=1, timeout=8)
        else:
            self.start()
            info = self.browser.devices.get(UUID(device_id)) if self.browser else None
            if not info:
                raise ValueError("ტელევიზორი ვერ მოიძებნა. განაახლე სია ან მიუთითე IP მისამართი.")
            cc = pychromecast.get_chromecast_from_cast_info(info, self.zconf, tries=1, timeout=8)
        try:
            cc.wait(timeout=12)
        except Exception:
            cc.disconnect()
            raise ValueError("TV-ს ვერ დავუკავშირდი. შეამოწმე, რომ Mac და TV ერთ ქსელშია და Google Cast ჩართულია.")
        old = self.cast
        self.cast = cc
        if old:
            old.disconnect()

    def play(self, movie, source, start=0):
        if not self.cast:
            raise ValueError("ჯერ აირჩიე ტელევიზორი.")
        title = movie["title"] + (" · " + source["episode"] if source["episode"] else "")
        # quick_play waits for the receiver's LOAD response and propagates failures.
        self.cast.media_controller.quick_play(media_id=source["url"], media_type=source["contentType"],
                                             timeout=25, title=title, thumb=movie["image"] or None,
                                             stream_type="BUFFERED", current_time=start)
        self.active = {"title": title, "movieUrl": movie["url"], "sourceId": source["id"]}
        return self.status()

    def status(self):
        if not self.cast:
            return {"connected": False}
        media = self.cast.media_controller.status
        state = media.player_state
        return {"connected": self.cast.socket_client.is_connected, "device": self.cast.cast_info.friendly_name,
                "state": state, "time": media.adjusted_current_time if state == "PLAYING" else media.current_time,
                "duration": media.duration or 0, "volume": self.cast.status.volume_level,
                "muted": self.cast.status.volume_muted, "idleReason": media.idle_reason,
                "active": self.active, "updatedAt": time.time()}

    def command(self, action, value=None):
        if not self.cast:
            raise ValueError("ჯერ აირჩიე ტელევიზორი.")
        mc = self.cast.media_controller
        if action in ("play", "pause", "stop"):
            getattr(mc, action)()
        elif action in ("seek", "volume"):
            value = float(value)
            if not math.isfinite(value) or value < 0:
                raise ValueError("არასწორი მნიშვნელობა.")
            if action == "seek":
                mc.seek(min(value, mc.status.duration) if mc.status.duration else value)
            else:
                self.cast.set_volume(min(1, value))
        elif action == "disconnect":
            self.cast.disconnect()
            self.cast, self.active = None, None
        else:
            raise ValueError("უცნობი მოქმედება.")
        return self.status()

    def close(self):
        if self.cast:
            self.cast.disconnect()
        if self.browser:
            self.browser.stop_discovery()
        if self.zconf:
            self.zconf.close()
