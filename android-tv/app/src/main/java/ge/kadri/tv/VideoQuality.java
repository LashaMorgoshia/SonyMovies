package ge.kadri.tv;

import android.content.SharedPreferences;
import org.jsoup.Jsoup;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Read actual HLS variants instead of trusting the movie page's quality label. */
final class VideoQuality {
    static final Pattern RESOLUTION=Pattern.compile("(?:^|,)RESOLUTION=\\d+x(\\d+)(?:,|$)");
    static int preferred(SharedPreferences prefs){return prefs.contains("qualityHeight")?prefs.getInt("qualityHeight",0):prefs.getInt("quality",0)==1?720:0;}
    static void save(SharedPreferences prefs,int height){prefs.edit().putInt("qualityHeight",height).apply();}
    static String label(int height){return height>0?height+"p":"ავტომატური · 1080p-მდე";}

    static List<Integer> parseHeights(String playlist){
        TreeSet<Integer> heights=new TreeSet<>();
        for(String raw:playlist.split("\\r?\\n")){
            String line=raw.trim();
            if(!line.startsWith("#EXT-X-STREAM-INF:"))continue;
            Matcher m=RESOLUTION.matcher(line.substring(line.indexOf(':')+1));
            if(m.find()){int h=Integer.parseInt(m.group(1));if(h>0&&h<=16384)heights.add(h);}
        }
        return new ArrayList<>(heights);
    }

    static List<Integer> available(String raw,String referer) throws Exception {
        String url=Repository.mediaUrl(raw);
        if(url==null||!URI.create(url).getPath().endsWith(".m3u8"))return Collections.emptyList();
        for(int n=0;n<4;n++){
            org.jsoup.Connection.Response r=Jsoup.connect(url).userAgent("Mozilla/5.0 KadriTV/1.0").referrer(referer).ignoreContentType(true).ignoreHttpErrors(true).maxBodySize(512000).timeout(10000).followRedirects(false).execute();
            if(r.statusCode()>=300&&r.statusCode()<400&&r.hasHeader("Location")){
                url=Repository.mediaUrl(URI.create(url).resolve(r.header("Location")).toString());
                if(url==null)throw new IllegalStateException("ვიდეოს გადამისამართება მხარდაჭერილი არ არის.");
                continue;
            }
            if(r.statusCode()!=200)throw new IllegalStateException("ხარისხების სია დროებით მიუწვდომელია.");
            return parseHeights(r.body());
        }
        throw new IllegalStateException("ვიდეოს გადამისამართება ვერ დასრულდა.");
    }
}
