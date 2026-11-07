"""Read public MyKadri metadata and JSON player configuration, without executing scripts."""
import json
import re
from urllib.parse import urljoin, urlsplit

import requests
from bs4 import BeautifulSoup

HOME = "https://mykadri.tv/"
EXAMPLE = HOME + "11881-vijenqvesti-visionquest-2.html"
PAGE_HOSTS = {"mykadri.tv", "www.mykadri.tv", "mykadri.com", "www.mykadri.com"}
# MyKadri publishes direct video on its own CDN domains and JustFile.
MEDIA_DOMAINS = ("mykadri.net", "mykadri.tv", "mykadri.com", "mykadricdn.store", "justfile.store")
AGENT = "Mozilla/5.0 KadriTV/1.0"


def page_url(url):
    parts = urlsplit(str(url).strip())
    if parts.scheme != "https" or parts.hostname not in PAGE_HOSTS or parts.port not in (None, 443) or parts.username:
        raise ValueError("ჩასვი MyKadri-ის HTTPS ბმული.")
    return parts.geturl()


def media_url(url):
    parts = urlsplit(str(url).strip())
    host = parts.hostname or ""
    if parts.scheme != "https" or parts.username or parts.port not in (None, 443):
        return None
    if not any(host == d or host.endswith("." + d) for d in MEDIA_DOMAINS):
        return None
    if not re.search(r"\.(m3u8|mp4)(?:$|\?)", parts.path + ("?" + parts.query if parts.query else ""), re.I):
        return None
    return parts.geturl()


def fetch_page(url, params=None):
    url = page_url(url)
    for _ in range(5):
        response = requests.get(url, params=params, headers={"User-Agent": AGENT}, timeout=(8, 25), allow_redirects=False)
        params = None
        if response.is_redirect:
            url = page_url(urljoin(url, response.headers["Location"]))
            continue
        response.raise_for_status()
        if len(response.content) > 4_000_000:
            raise ValueError("გვერდი ზედმეტად დიდია.")
        response.encoding = "utf-8"
        return response.text, response.url
    raise ValueError("MyKadri-ის გადამისამართება ვერ დასრულდა.")


def parse_movie(html, url):
    soup = BeautifulSoup(html, "html.parser")
    def text(selector, default=""):
        el = soup.select_one(selector)
        return el.get_text(" ", strip=True) if el else default
    def meta(name):
        el = soup.find("meta", property=name)
        return el.get("content", "") if el else ""
    title = text("h1", meta("og:title").split("|")[0]).replace(" - ქართულად", "").strip()
    entries, seen = [], set()

    def walk(value, season="", episode="", language="ქართული"):
        if isinstance(value, list):
            for item in value:
                walk(item, season, episode, language)
        elif isinstance(value, dict):
            label = language if "file" in value else value.get("label", language)
            name = value.get("title", episode)
            source = media_url(value.get("file", ""))
            if source:
                key = (source, season, name, label)
                if key not in seen:
                    seen.add(key)
                    entries.append({"id": str(len(entries)), "url": source, "season": str(season),
                                    "episode": name, "language": label, "quality": value.get("label", "ავტომატური"),
                                    "contentType": "application/x-mpegURL" if ".m3u8" in source else "video/mp4"})
            for key, item in value.items():
                if isinstance(item, (dict, list)):
                    walk(item, key if key.isdigit() else season, name, label)

    decoder = json.JSONDecoder()
    for script in soup.find_all("script"):
        raw = script.string or script.get_text()
        for match in re.finditer(r"\b(?:movie|playlist|sources)\s*:\s*", raw):
            try:
                config, _ = decoder.raw_decode(raw[match.end():].lstrip())
                walk(config)
            except (ValueError, TypeError):
                continue
    if not entries:
        for raw in re.findall(r'https?[^\s<>"\']+\.(?:m3u8|mp4)(?:\?[^\s<>"\']*)?', html.replace("\\/", "/")):
            source = media_url(raw)
            if source and source not in seen:
                seen.add(source)
                entries.append({"id": str(len(entries)), "url": source, "season": "", "episode": "",
                                "language": "ქართული", "quality": "ავტომატური",
                                "contentType": "application/x-mpegURL" if ".m3u8" in source else "video/mp4"})
    if not title:
        raise ValueError("ფილმის გვერდი ვერ ამოვიცანი.")
    return {"title": title, "englishTitle": text(".fullstory-title-en"), "description": meta("og:description"),
            "image": meta("og:image"), "url": url, "sources": entries,
            "notice": "" if entries else "ამ გვერდზე პირდაპირი ქართული ვიდეონაკადი ვერ მოიძებნა. გარე ჩაშენებული ფლეერები ჯერ არ არის მხარდაჭერილი."}


def parse_catalog(html):
    soup = BeautifulSoup(html, "html.parser")
    result, seen = [], set()
    cards = soup.select("article.poster-card, article.landscape-card") or soup.select("article.hero-slide")
    for card in cards:
        link = card.select_one("a.card-media-link, a.card-info, a[href]")
        url = card.get("data-href") or (link.get("href") if link else "")
        try:
            url = page_url(urljoin(HOME, url))
        except ValueError:
            continue
        if not urlsplit(url).path.endswith(".html") or url in seen:
            continue
        seen.add(url)
        title = card.select_one(".card-primary-title, .hero-logo")
        image = card.select_one("img")
        source = card.select_one("source[srcset]")
        name = image.get("alt", "") if image else ""
        picture = source.get("srcset", "").split()[0] if source else (image.get("data-src") or image.get("src", "") if image else "")
        picture = re.sub(r"(https://image\.tmdb\.org/t/p/)(?:w\d+|original)/", r"\g<1>w342/", picture)
        result.append({"url": url, "title": name or (title.get_text(" ", strip=True) if title else "ფილმი"), "image": picture})
    return result[:60]
