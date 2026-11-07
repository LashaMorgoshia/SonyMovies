package ge.kadri.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import org.jsoup.Jsoup;
import java.nio.file.*;

public class RepositoryTest {
    private String fixture(String name) throws Exception { return new String(Files.readAllBytes(Paths.get(System.getProperty("fixtureDir"),name)),java.nio.charset.StandardCharsets.UTF_8); }
    @Test public void seriesKeepsAllSixGeorgianEpisodes() throws Exception {
        JSONObject movie=Repository.parseMovie(Jsoup.parse(fixture("visionquest.html")),Repository.EXAMPLE);
        assertEquals("ვიჟენქვესთი",movie.getString("title"));JSONArray sources=movie.getJSONArray("sources");assertEquals(6,sources.length());
        for(int i=0;i<6;i++){JSONObject s=sources.getJSONObject(i);assertEquals("სერია "+(i+1),s.getString("episode"));assertEquals("ქართული",s.getString("language"));assertEquals("1",s.getString("season"));}
    }
    @Test public void qualityIsNotTreatedAsLanguage() throws Exception {
        String html="<h1>ფილმი</h1><script>let x={sources:[{\"file\":\"https://cdn.mykadri.net/a.mp4\",\"label\":\"1080p\"}]};</script>";
        JSONObject source=Repository.parseMovie(Jsoup.parse(html),Repository.EXAMPLE).getJSONArray("sources").getJSONObject(0);
        assertEquals("ქართული",source.getString("language"));assertEquals("1080p",source.getString("quality"));
    }
    @Test public void guardiansJustfileMovieHasWatchSource() throws Exception {
        JSONObject movie=Repository.parseMovie(Jsoup.parse(fixture("guardians.html")),Repository.EXAMPLE);
        assertEquals("გალაქტიკის მცველები",movie.getString("title"));
        JSONArray sources=movie.getJSONArray("sources");assertEquals(1,sources.length());
        JSONObject source=sources.getJSONObject(0);
        assertTrue(source.getString("url").startsWith("https://cdn2.justfile.store/"));
        assertEquals("ქართული",source.getString("language"));assertEquals("720p",source.getString("quality"));
    }
    @Test public void guardiansSequelKeepsTheSignedGeorgianSourceOnTheNewCdn() throws Exception {
        JSONObject movie=Repository.parseMovie(Jsoup.parse(fixture("guardians2.html")),Repository.HOME+"4438-guardians-vol-2.html");
        assertEquals("გალაქტიკის მცველები 2",movie.getString("title"));
        JSONArray sources=movie.getJSONArray("sources");assertEquals(1,sources.length());
        JSONObject source=sources.getJSONObject(0);
        assertEquals("https://storage.mykadricdn.store/storage/playlists/2025-04/40901/1/2n5d05U4Yp.m3u8?md5=fixture-signature&expires=1794675600&sid=fixture-session",source.getString("url"));
        assertEquals("ქართული",source.getString("language"));assertEquals("720p",source.getString("quality"));assertFalse(movie.getBoolean("externalOnly"));
    }
    @Test public void newCdnSupportKeepsHostAndHttpsRestrictions() {
        assertEquals("https://storage.mykadricdn.store/a.m3u8?md5=test&expires=123&sid=test",Repository.mediaUrl("https://storage.mykadricdn.store/a.m3u8?md5=test&expires=123&sid=test"));
        for(String url:new String[]{"https://mykadricdn.store.evil.test/a.m3u8","https://notmykadricdn.store/a.m3u8","http://storage.mykadricdn.store/a.m3u8","https://user@storage.mykadricdn.store/a.m3u8","https://storage.mykadricdn.store:8000/a.m3u8","https://storage.mykadricdn.store/player.html"})assertNull(Repository.mediaUrl(url));
    }
    @Test public void movieWrapperKeepsLanguageAndQuality() throws Exception {
        String html="<h1>ფილმი</h1><script>let x={movie:[{\"label\":\"ინგლისური\",\"sources\":[{\"file\":\"https://cdn2.justfile.store/a.m3u8\",\"label\":\"1080p\"}]}]};</script>";
        JSONArray sources=Repository.parseMovie(Jsoup.parse(html),Repository.EXAMPLE).getJSONArray("sources");assertEquals(1,sources.length());
        assertEquals("ინგლისური",sources.getJSONObject(0).getString("language"));assertEquals("1080p",sources.getJSONObject(0).getString("quality"));
    }
    @Test public void justfileLookalikeHostsAreRejected() {
        assertEquals("https://cdn2.justfile.store/a.m3u8?token=test",Repository.mediaUrl("https://cdn2.justfile.store/a.m3u8?token=test"));
        for(String url:new String[]{"https://justfile.store.evil.test/a.m3u8","https://notjustfile.store/a.m3u8","http://cdn2.justfile.store/a.m3u8","https://user@cdn2.justfile.store/a.m3u8","https://cdn2.justfile.store:8000/a.m3u8"})assertNull(Repository.mediaUrl(url));
    }
    @Test public void catalogDoesNotDuplicateMovies() throws Exception {
        String html=fixture("home.html");assertEquals(6,Repository.parseCatalog(Jsoup.parse(html+html,Repository.HOME)).length());
    }
    @Test public void categorySelectionLoadsItsOwnCatalogueInsteadOfTheHomePage() throws Exception {
        CatalogCategory series=CatalogCategory.ITEMS.stream().filter(c->c.title.equals("სერიალები")).findFirst().get();
        CatalogCategory comedy=CatalogCategory.ITEMS.stream().filter(c->c.title.equals("კომედია")).findFirst().get();
        assertEquals("https://mykadri.tv/serialebi_qartulad/",Repository.catalogUrl("",series));
        assertEquals("https://mykadri.tv/filmebiqartulad/komedia/",Repository.catalogUrl("  ",comedy));
        assertEquals(Repository.HOME,Repository.catalogUrl("",CatalogCategory.ALL));
    }
    @Test public void searchRemainsGlobalAfterSelectingACategoryAndEncodesTheQuery() throws Exception {
        String query="გრუტი & Groot?";
        String url=Repository.catalogUrl("  "+query+"  ",CatalogCategory.ITEMS.get(2));
        assertTrue(url.startsWith(Repository.HOME+"?do=search&subaction=search&story="));
        assertEquals(query,java.net.URLDecoder.decode(url.split("story=",2)[1],"UTF-8"));
    }
    @Test public void externalAndLocalUrlsAreRejected() {
        for(String url:new String[]{"http://mykadri.tv/", "https://mykadri.tv.evil.test/", "https://127.0.0.1/", "https://user@mykadri.tv/"}) {try{Repository.pageUrl(url);fail(url);}catch(IllegalArgumentException expected){}}
        assertNull(Repository.mediaUrl("https://evil.test/a.m3u8"));
    }
    @Test public void embedOnlyHasNoInventedSource() throws Exception {assertEquals(0,Repository.parseMovie(Jsoup.parse("<h1>ფილმი</h1><iframe src='https://example.test/embed'></iframe>"),Repository.EXAMPLE).getJSONArray("sources").length());}
    @Test public void grootIsReportedAsExternalOnlyWithoutInventingANativeWatchSource() throws Exception {
        JSONObject movie=Repository.parseMovie(Jsoup.parse(fixture("groot.html")),"https://mykadri.tv/7697-me-var-gruti-i-am-groot.html");
        assertEquals("მე ვარ გრუტი",movie.getString("title"));assertEquals(0,movie.getJSONArray("sources").length());
        assertTrue(movie.getBoolean("externalOnly"));assertFalse(movie.has("embeds"));
    }
    @Test public void nativeMovieKeepsItsSourceWhenAnExternalAlternativeExists() throws Exception {
        String html="<h1>ფილმი</h1><script>let x={sources:[{\"file\":\"https://cdn.mykadri.net/a.mp4\",\"label\":\"1080p\"}]};</script><div class='language-player-panel'><iframe data-src='https://vidsrc.sh/embed/movie/tt123' src='about:blank'></iframe></div>";
        JSONObject movie=Repository.parseMovie(Jsoup.parse(html),Repository.EXAMPLE);
        assertEquals(1,movie.getJSONArray("sources").length());assertFalse(movie.getBoolean("externalOnly"));assertFalse(movie.has("embeds"));
    }
}
