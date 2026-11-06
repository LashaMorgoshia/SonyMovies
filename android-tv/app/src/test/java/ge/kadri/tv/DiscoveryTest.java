package ge.kadri.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;
import java.util.*;

public class DiscoveryTest {
    @Test public void hlsMasterListsActualQualitiesDespiteA720pPageLabel(){
        String playlist="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=4,RESOLUTION=1280x720,CODECS=\"avc1\"\n720p/video.m3u8\n#EXT-X-STREAM-INF:RESOLUTION=1920x1080,BANDWIDTH=8\n1080p/video.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=25,RESOLUTION=2560x1440\n1440p/video.m3u8\n";
        assertEquals(Arrays.asList(720,1080,1440),VideoQuality.parseHeights(playlist));
    }
    @Test public void duplicateVariantsDoNotDuplicateQualityChoices(){
        assertEquals(Arrays.asList(720),VideoQuality.parseHeights("#EXT-X-STREAM-INF:RESOLUTION=1280x720\na.m3u8\n#EXT-X-STREAM-INF:RESOLUTION=1280x720\nb.m3u8"));
    }
    @Test public void singleRenditionDoesNotInventHigherQualities(){assertTrue(VideoQuality.parseHeights("#EXTM3U\n#EXTINF:4,\nvideo.ts\n").isEmpty());}
    @Test public void launcherMovieLinkRoundTripsThePageUrl(){
        String url="https://mykadri.tv/2717-guardians.html?lang=geo&source=1";assertEquals(url,DeepLinks.movieUrl(DeepLinks.movie(url)));
    }
    @Test public void launcherLinksRejectForeignWebsitesAndLocalFiles(){
        for(String link:new String[]{"kadritv://movie?url=https%3A%2F%2Fevil.test%2Fmovie","kadritv://movie?url=file%3A%2F%2F%2Fdata%2Flocal","kadritv://movie?url=https%3A%2F%2Fmykadri.tv%2Fa.html&url=https%3A%2F%2Fmykadri.tv%2Fb.html"}){
            try{DeepLinks.movieUrl(link);fail(link);}catch(IllegalArgumentException expected){}
        }
        assertNull(DeepLinks.movieUrl("https://example.test/"));
    }
    @Test public void recommendationSelectionIsBoundedUniqueAndRespectsDismissals() throws Exception {
        JSONArray catalog=new JSONArray();for(int i=0;i<20;i++)catalog.put(movie(i));catalog.put(movie(1));
        List<JSONObject> chosen=HomeRecommendations.pick(new JSONArray(),catalog,Collections.singleton("https://mykadri.tv/1-movie.html"));
        assertEquals(12,chosen.size());Set<String> urls=new HashSet<>();for(JSONObject item:chosen){assertFalse(item.getString("url").contains("/1-movie"));assertTrue(urls.add(item.getString("url")));}
    }
    @Test public void invalidRecommendationLinksAreNotPublished() throws Exception {
        JSONArray catalog=new JSONArray().put(new JSONObject().put("title","bad").put("url","https://evil.test/a.html").put("image","https://image.tmdb.org/a.jpg")).put(movie(3));
        assertEquals(1,HomeRecommendations.pick(new JSONArray(),catalog,Collections.emptySet()).size());
    }
    @Test public void recentMoviesLeadInHistoryOrderWithoutRepeatingSuggestions() throws Exception {
        JSONArray recent=new JSONArray().put(movie(77)).put(movie(3)).put(movie(2)).put(movie(3));
        JSONArray suggestions=new JSONArray();for(int i=0;i<20;i++)suggestions.put(movie(i));
        List<JSONObject> chosen=HomeRecommendations.pick(recent,suggestions,Collections.emptySet());
        assertEquals(12,chosen.size());assertEquals(movie(77).getString("url"),chosen.get(0).getString("url"));assertEquals(movie(3).getString("url"),chosen.get(1).getString("url"));assertEquals(movie(2).getString("url"),chosen.get(2).getString("url"));
        Set<String> urls=new HashSet<>();for(JSONObject item:chosen)assertTrue(urls.add(item.getString("url")));
    }
    @Test public void fullHistoryKeepsOnlyTheTwelveMostRecentCards() throws Exception {
        JSONArray recent=new JSONArray();for(int i=30;i>=0;i--)recent.put(movie(i));
        List<JSONObject> chosen=HomeRecommendations.pick(recent,new JSONArray().put(movie(100)),Collections.emptySet());
        assertEquals(12,chosen.size());for(int i=0;i<12;i++)assertEquals(movie(30-i).getString("url"),chosen.get(i).getString("url"));
    }
    @Test public void recentCardsWorkWithoutACatalogAndRespectDismissals() throws Exception {
        JSONArray recent=new JSONArray().put(movie(9)).put(new JSONObject().put("url","https://evil.test/a.html").put("title","bad").put("image","https://image.tmdb.org/a.jpg")).put(movie(4));
        List<JSONObject> chosen=HomeRecommendations.pick(recent,new JSONArray(),Collections.singleton(movie(9).getString("url")));
        assertEquals(1,chosen.size());assertEquals(movie(4).getString("url"),chosen.get(0).getString("url"));
    }
    @Test public void openingAMovieDoesNotShuffleTheRemainingSuggestions() throws Exception {
        JSONArray suggestions=new JSONArray().put(movie(5)).put(movie(3)).put(movie(8)).put(movie(1));
        List<JSONObject> chosen=HomeRecommendations.pick(new JSONArray().put(movie(8)),suggestions,Collections.emptySet());
        assertEquals(Arrays.asList(8,5,3,1),Arrays.asList(id(chosen.get(0)),id(chosen.get(1)),id(chosen.get(2)),id(chosen.get(3))));
    }
    private int id(JSONObject item){return Integer.parseInt(item.optString("url").split("/")[3].split("-")[0]);}
    private JSONObject movie(int id) throws Exception{return new JSONObject().put("title","Movie "+id).put("url","https://mykadri.tv/"+id+"-movie.html").put("image","https://image.tmdb.org/t/p/w342/a"+id+".jpg");}
}
