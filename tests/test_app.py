import json
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "mac"))
from mykadri import parse_movie, parse_catalog, page_url, media_url, fetch_page, EXAMPLE
from casting import CastManager
import server

FIXTURES = Path(__file__).parent / "fixtures"


class ParserTest(unittest.TestCase):
    def test_real_series_preserves_episode_order_and_language(self):
        movie = parse_movie((FIXTURES / "visionquest.html").read_text(), EXAMPLE)
        self.assertEqual(movie["title"], "ვიჟენქვესთი")
        self.assertEqual(len(movie["sources"]), 6)
        self.assertEqual([s["episode"] for s in movie["sources"]], [f"სერია {i}" for i in range(1, 7)])
        self.assertEqual({s["language"] for s in movie["sources"]}, {"ქართული"})
        self.assertEqual({s["season"] for s in movie["sources"]}, {"1"})

    def test_movie_quality_does_not_replace_language(self):
        html = '<h1>ფილმი</h1><script>let x={sources:[{"file":"https://cdn.mykadri.net/a.mp4","label":"1080p"}]};</script>'
        source = parse_movie(html, EXAMPLE)["sources"][0]
        self.assertEqual(source["language"], "ქართული")
        self.assertEqual(source["quality"], "1080p")

    def test_guardians_justfile_movie_has_watch_source(self):
        movie = parse_movie((FIXTURES / "guardians.html").read_text(), EXAMPLE)
        self.assertEqual(movie["title"], "გალაქტიკის მცველები")
        self.assertEqual(len(movie["sources"]), 1)
        source = movie["sources"][0]
        self.assertTrue(source["url"].startswith("https://cdn2.justfile.store/"))
        self.assertEqual(source["language"], "ქართული")
        self.assertEqual(source["quality"], "720p")
        self.assertEqual(source["contentType"], "application/x-mpegURL")
        self.assertEqual(movie["notice"], "")

    def test_guardians_sequel_keeps_signed_georgian_source_on_new_cdn(self):
        movie = parse_movie((FIXTURES / "guardians2.html").read_text(), EXAMPLE)
        self.assertEqual(movie["title"], "გალაქტიკის მცველები 2")
        self.assertEqual(len(movie["sources"]), 1)
        source = movie["sources"][0]
        self.assertEqual(source["url"], "https://storage.mykadricdn.store/storage/playlists/2025-04/40901/1/2n5d05U4Yp.m3u8?md5=fixture-signature&expires=1794675600&sid=fixture-session")
        self.assertEqual(source["language"], "ქართული")
        self.assertEqual(source["quality"], "720p")
        self.assertEqual(source["contentType"], "application/x-mpegURL")
        self.assertEqual(movie["notice"], "")

    def test_new_cdn_support_keeps_host_and_https_restrictions(self):
        valid = "https://storage.mykadricdn.store/a.m3u8?md5=test&expires=123&sid=test"
        self.assertEqual(media_url(valid), valid)
        for url in ("https://mykadricdn.store.evil.test/a.m3u8", "https://notmykadricdn.store/a.m3u8", "http://storage.mykadricdn.store/a.m3u8", "https://user@storage.mykadricdn.store/a.m3u8", "https://storage.mykadricdn.store:8000/a.m3u8", "https://storage.mykadricdn.store/player.html"):
            with self.subTest(url=url):
                self.assertIsNone(media_url(url))

    def test_movie_wrapper_keeps_language_and_quality(self):
        html = '<h1>ფილმი</h1><script>let x={movie:[{"label":"ინგლისური","sources":[{"file":"https://cdn2.justfile.store/a.m3u8","label":"1080p"}]}]};</script>'
        sources = parse_movie(html, EXAMPLE)["sources"]
        self.assertEqual(len(sources), 1)
        self.assertEqual(sources[0]["language"], "ინგლისური")
        self.assertEqual(sources[0]["quality"], "1080p")

    def test_justfile_support_does_not_accept_lookalike_hosts(self):
        self.assertEqual(media_url("https://cdn2.justfile.store/a.m3u8?token=test"), "https://cdn2.justfile.store/a.m3u8?token=test")
        for url in ("https://justfile.store.evil.test/a.m3u8", "https://notjustfile.store/a.m3u8", "http://cdn2.justfile.store/a.m3u8", "https://user@cdn2.justfile.store/a.m3u8", "https://cdn2.justfile.store:8000/a.m3u8"):
            with self.subTest(url=url):
                self.assertIsNone(media_url(url))

    def test_embed_only_is_reported_without_fake_media(self):
        movie = parse_movie('<h1>ფილმი</h1><iframe src="https://external.test/embed"></iframe>', EXAMPLE)
        self.assertEqual(movie["sources"], [])
        self.assertTrue(movie["notice"])

    def test_untrusted_urls_rejected(self):
        for url in ["http://mykadri.tv/a", "https://mykadri.tv.evil.test/", "https://127.0.0.1/", "https://user@mykadri.tv/", "https://mykadri.tv:8000/"]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                page_url(url)
        self.assertIsNone(media_url("https://evil.test/a.m3u8"))

    def test_redirects_are_validated(self):
        response = Mock(is_redirect=True, headers={"Location": "http://127.0.0.1/private"})
        with patch("mykadri.requests.get", return_value=response), self.assertRaises(ValueError):
            fetch_page(EXAMPLE)

    def test_catalog_deduplicates(self):
        html = (FIXTURES / "home.html").read_text()
        items = parse_catalog(html + html)
        self.assertEqual(len(items), 6)
        self.assertTrue(all("w342" in x["image"] for x in items))


class ApiTest(unittest.TestCase):
    def setUp(self):
        self.client = server.app.test_client()
        self.base = f"http://localhost:{server.PORT}"
        self.headers = {"Origin": self.base, "X-Kadri-Token": server.token}

    def test_cross_origin_control_is_blocked(self):
        r = self.client.post("/api/control", base_url=self.base, headers={"Origin": "https://evil.test"}, json={"action": "stop"})
        self.assertEqual(r.status_code, 403)

    def test_token_is_required(self):
        r = self.client.post("/api/control", base_url=self.base, headers={"Origin": self.base}, json={"action": "stop"})
        self.assertEqual(r.status_code, 403)

    def test_resolve_and_cast_uses_server_source(self):
        html = (FIXTURES / "visionquest.html").read_text()
        with patch("server.fetch_page", return_value=(html, EXAMPLE)):
            r = self.client.post("/api/resolve", base_url=self.base, headers=self.headers, json={"url": EXAMPLE})
        self.assertEqual(r.status_code, 200)
        with patch.object(server.manager, "play", return_value={"connected": True}) as play:
            r = self.client.post("/api/cast", base_url=self.base, headers=self.headers, json={"url": EXAMPLE, "sourceId": "0", "mediaUrl": "http://evil.test"})
            self.assertEqual(r.status_code, 200)
            self.assertTrue(play.call_args.args[1]["url"].startswith("https://cdn2.mykadri.net/"))


class CastTest(unittest.TestCase):
    def test_private_ipv4_required(self):
        manager = CastManager()
        for host in ["127.0.0.1", "8.8.8.8", "169.254.169.254", "::1"]:
            with self.subTest(host=host), self.assertRaises(ValueError):
                manager.connect(host=host)

    def test_load_failure_is_not_reported_as_success(self):
        manager = CastManager()
        manager.cast = Mock()
        manager.cast.media_controller.quick_play.side_effect = RuntimeError("LOAD_FAILED")
        movie = parse_movie((FIXTURES / "visionquest.html").read_text(), EXAMPLE)
        with self.assertRaises(RuntimeError):
            manager.play(movie, movie["sources"][0])
        self.assertIsNone(manager.active)


if __name__ == "__main__":
    unittest.main()
