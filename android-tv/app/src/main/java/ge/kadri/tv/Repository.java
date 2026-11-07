package ge.kadri.tv;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Read public metadata and direct video sources without running embedded players. */
public final class Repository {
    public static final String HOME = "https://mykadri.tv/";
    public static final String EXAMPLE = HOME + "11881-vijenqvesti-visionquest-2.html";
    private static final Pattern CONFIG = Pattern.compile("\\b(?:movie|playlist|sources)\\s*:\\s*");
    private static final Set<String> HOSTS = new HashSet<>(Arrays.asList("mykadri.tv", "www.mykadri.tv", "mykadri.com", "www.mykadri.com"));
    private static final List<String> MEDIA_DOMAINS = Arrays.asList("mykadri.net", "mykadri.tv", "mykadri.com", "mykadricdn.store", "justfile.store");
    public static String pageUrl(String raw) {
        URI uri = URI.create(raw.trim());
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost()) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException("ჩაწერე MyKadri-ის HTTPS ბმული.");
        return uri.toString();
    }
    static String mediaUrl(String raw) {
        try {
            URI u = URI.create(raw.trim()); String h = u.getHost();
            if (!"https".equals(u.getScheme()) || h == null || u.getUserInfo() != null || (u.getPort() != -1 && u.getPort() != 443)) return null;
            boolean allowed = false;
            for (String d : MEDIA_DOMAINS) if (h.equals(d) || h.endsWith("." + d)) allowed = true;
            if (!allowed || !u.getPath().toLowerCase(Locale.ROOT).matches(".*\\.(m3u8|mp4)$")) return null;
            return u.toString();
        } catch (Exception e) { return null; }
    }
    static Document fetch(String raw) throws Exception {
        String url = pageUrl(raw);
        for (int n=0; n<5; n++) {
            org.jsoup.Connection.Response r = Jsoup.connect(url).userAgent("Mozilla/5.0 KadriTV/1.0").timeout(25000).maxBodySize(4000000).followRedirects(false).ignoreHttpErrors(true).execute();
            if (r.statusCode() >= 300 && r.statusCode() < 400 && r.hasHeader("Location")) { url = pageUrl(URI.create(url).resolve(r.header("Location")).toString()); continue; }
            if (r.statusCode() != 200) throw new IllegalStateException("MyKadri მიუწვდომელია (" + r.statusCode() + "). სცადე ხელახლა.");
            return r.parse();
        }
        throw new IllegalStateException("გვერდის გადამისამართება ვერ დასრულდა.");
    }
    public static JSONObject movie(String url) throws Exception { Document d = fetch(url); return parseMovie(d, d.location()); }
    static String text(Document d, String selector) { Element e = d.selectFirst(selector); return e == null ? "" : e.text(); }
    static String meta(Document d, String property) { Element e=d.selectFirst("meta[property=" + property + "]"); return e==null ? "" : e.attr("content"); }
    public static JSONObject parseMovie(Document doc, String url) throws Exception {
        JSONArray sources = new JSONArray(); Set<String> seen = new HashSet<>();
        for (Element script: doc.select("script")) {
            String raw=script.data(); Matcher m=CONFIG.matcher(raw);
            while(m.find()) {
                try { walk(new JSONTokener(raw.substring(m.end())).nextValue(), "", "", "ქართული", sources, seen); } catch (JSONException ignored) { }
            }
        }
        if (sources.length()==0) {
            Matcher links=Pattern.compile("https?[^\\s<>\"']+\\.(?:m3u8|mp4)(?:\\?[^\\s<>\"']*)?").matcher(doc.html().replace("\\/", "/"));
            while(links.find()) { String source=mediaUrl(links.group()); if(source!=null && seen.add(source)) sources.put(new JSONObject().put("id",String.valueOf(sources.length())).put("url",source).put("season", "").put("episode", "").put("language", "ქართული").put("quality", "ავტომატური")); }
        }
        String title=text(doc,"h1"); if(title.isEmpty()) title=meta(doc,"og:title").split("\\|")[0];
        title=title.replace(" - ქართულად", "").trim();
        if(title.isEmpty()) throw new IllegalStateException("ფილმის გვერდი ვერ ამოვიცანი.");
        boolean hasEmbeddedPlayer=false;
        for(Element iframe:doc.select(".language-player-panel iframe")){
            String link=iframe.attr("data-src");if(link.isEmpty()||link.equals("about:blank"))link=iframe.attr("src");
            try{URI frame=URI.create(link);if("https".equals(frame.getScheme())&&frame.getHost()!=null)hasEmbeddedPlayer=true;}catch(IllegalArgumentException ignored){}
        }
        return new JSONObject().put("title",title).put("englishTitle",text(doc,".fullstory-title-en")).put("description",meta(doc,"og:description")).put("image",meta(doc,"og:image")).put("url",url).put("sources",sources).put("externalOnly",sources.length()==0&&hasEmbeddedPlayer);
    }
    static void walk(Object value, String season, String episode, String language, JSONArray out, Set<String> seen) throws JSONException {
        if(value instanceof JSONArray) { JSONArray arr=(JSONArray)value; for(int i=0;i<arr.length();i++) walk(arr.get(i),season,episode,language,out,seen); }
        else if(value instanceof JSONObject) {
            JSONObject obj=(JSONObject)value; String label=obj.has("file")?language:obj.optString("label",language), title=obj.optString("title",episode), file=mediaUrl(obj.optString("file"));
            if(file!=null && seen.add(file+"|"+season+"|"+title+"|"+label)) out.put(new JSONObject().put("id",String.valueOf(out.length())).put("url",file).put("season",season).put("episode",title).put("language",label).put("quality",obj.optString("label","ავტომატური")));
            Iterator<String> keys=obj.keys(); while(keys.hasNext()) { String key=keys.next(); Object child=obj.get(key); if(child instanceof JSONObject || child instanceof JSONArray) walk(child,key.matches("\\d+")?key:season,title,label,out,seen); }
        }
    }
    public static JSONArray catalog(String query) throws Exception {
        return catalog(query,CatalogCategory.ALL);
    }
    static String catalogUrl(String query,CatalogCategory category) throws Exception {
        if(!query.trim().isEmpty())return HOME+"?do=search&subaction=search&story="+java.net.URLEncoder.encode(query.trim(),"UTF-8");
        return pageUrl(HOME+category.path);
    }
    static JSONArray catalog(String query,CatalogCategory category) throws Exception {
        return parseCatalog(fetch(catalogUrl(query,category)));
    }
    public static JSONArray parseCatalog(Document doc) throws JSONException {
        JSONArray items=new JSONArray(); Set<String> seen=new HashSet<>();
        org.jsoup.select.Elements cards=doc.select("article.poster-card, article.landscape-card");
        if(cards.isEmpty())cards=doc.select("article.hero-slide");
        for(Element card:cards) {
            Element a=card.selectFirst("a.card-media-link, a.card-info, a[href]"), image=card.selectFirst("img"), source=card.selectFirst("source[srcset]");
            String url=card.hasAttr("data-href")?card.attr("data-href"):(a==null?"":a.absUrl("href"));
            try { url=pageUrl(url); } catch(Exception e) { continue; }
            if(!URI.create(url).getPath().endsWith(".html") || !seen.add(url)) continue;
            String title=image==null?"":image.attr("alt");
            if(title.isEmpty()) { Element heading=card.selectFirst(".card-primary-title, .hero-logo"); title=heading==null?"ფილმი":heading.text(); }
            String picture=source!=null?source.attr("srcset").split("\\s")[0]:(image==null?"":image.hasAttr("data-src")?image.attr("data-src"):image.attr("src"));
            picture=picture.replaceAll("(https://image\\.tmdb\\.org/t/p/)(?:w\\d+|original)/","$1w342/");
            items.put(new JSONObject().put("url",url).put("title",title).put("image",picture));
            if(items.length()>=60) break;
        }
        return items;
    }
}
