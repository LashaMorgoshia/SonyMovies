package ge.kadri.tv;

import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import org.json.*;
import java.io.OutputStream;
import java.util.*;

/** One TV-home channel: recent movies first, followed by stable random suggestions. */
final class HomeRecommendations {
    static final int PERIODIC_JOB=19401, REFRESH_JOB=19402;
    static final long INTERVAL=6*60*60*1000L;
    private static final String CHANNEL_KEY="kadri-random-v1";

    static void schedule(Context context){
        if(Build.VERSION.SDK_INT<26)return;
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        ComponentName service=new ComponentName(context,RecommendationJobService.class);
        if(scheduler.getPendingJob(PERIODIC_JOB)==null)scheduler.schedule(new JobInfo.Builder(PERIODIC_JOB,service).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(INTERVAL).setPersisted(true).build());
    }
    static void requestRefresh(Context context){
        if(Build.VERSION.SDK_INT<26)return;
        schedule(context);
        SharedPreferences prefs=context.getSharedPreferences("tvhome",0);
        if(prefs.contains("suggestions")&&System.currentTimeMillis()-prefs.getLong("lastUpdate",0)<INTERVAL)return;
        context.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(REFRESH_JOB,new ComponentName(context,RecommendationJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setOverrideDeadline(0).build());
    }
    static void refresh(Context context) throws Exception {sync(context,Repository.catalog(""));}

    static List<JSONObject> pick(JSONArray recent,JSONArray suggestions,Set<String> hidden) {
        LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();
        for(JSONArray items:new JSONArray[]{recent,suggestions})for(int i=0;i<items.length()&&unique.size()<12;i++){
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            try{String url=Repository.pageUrl(item.optString("url"));java.net.URI image=java.net.URI.create(item.optString("image"));if(!hidden.contains(url)&&"https".equals(image.getScheme())&&image.getHost()!=null&&image.getUserInfo()==null&&!item.optString("title").trim().isEmpty()&&!unique.containsKey(url))unique.put(url,item);}catch(Exception ignored){}
        }
        return new ArrayList<>(unique.values());
    }

    static synchronized void sync(Context context,JSONArray catalog) throws Exception {
        if(Build.VERSION.SDK_INT<26)return;
        SharedPreferences prefs=context.getSharedPreferences("tvhome",0);
        if(catalog.length()>0&&(!prefs.contains("suggestions")||System.currentTimeMillis()-prefs.getLong("lastUpdate",0)>=INTERVAL)){
            List<JSONObject> suggestions=new ArrayList<>();for(int i=0;i<catalog.length();i++){JSONObject item=catalog.optJSONObject(i);if(item!=null)suggestions.add(item);}Collections.shuffle(suggestions);
            prefs.edit().putString("suggestions",new JSONArray(suggestions).toString()).putLong("lastUpdate",System.currentTimeMillis()).apply();
        }
        publish(context);
    }

    /** History updates never wait for the network or the six-hour suggestion refresh. */
    static synchronized void syncRecent(Context context) throws Exception {
        if(Build.VERSION.SDK_INT>=26)publish(context);
    }

    private static JSONArray array(String value){try{return new JSONArray(value);}catch(JSONException ignored){return new JSONArray();}}

    private static void publish(Context context) throws Exception {
        SharedPreferences prefs=context.getSharedPreferences("tvhome",0);
        long channel=ensureChannel(context);
        Set<String> hidden=new HashSet<>(prefs.getStringSet("hidden",Collections.emptySet()));
        JSONArray recent=array(context.getSharedPreferences("library",0).getString("recent","[]"));
        List<JSONObject> chosen=pick(recent,array(prefs.getString("suggestions","[]")),hidden);if(chosen.isEmpty())return;
        Set<String> recentUrls=new HashSet<>();for(int i=0;i<recent.length();i++){JSONObject item=recent.optJSONObject(i);if(item!=null)recentUrls.add(item.optString("url"));}
        ContentResolver resolver=context.getContentResolver();Map<String,Long> existing=new HashMap<>();
        try(Cursor cursor=resolver.query(TvContract.buildPreviewProgramsUriForChannel(channel),new String[]{"_id",TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID},null,null,null)){
            if(cursor!=null)while(cursor.moveToNext())existing.put(cursor.getString(1),cursor.getLong(0));
        }
        for(int i=0;i<chosen.size();i++){
            JSONObject item=chosen.get(i);String url=item.getString("url");
            ContentValues values=new ContentValues();
            values.put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,channel);
            values.put(TvContract.PreviewPrograms.COLUMN_TYPE,TvContract.PreviewPrograms.TYPE_MOVIE);
            values.put(TvContract.PreviewPrograms.COLUMN_TITLE,item.getString("title"));
            values.put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,(recentUrls.contains(url)?"ბოლოს ნანახი":"ფილმი ქართულად")+" · "+context.getString(R.string.app_name));
            values.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,item.getString("image"));
            values.put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,TvContract.PreviewPrograms.ASPECT_RATIO_2_3);
            values.put(TvContract.PreviewPrograms.COLUMN_INTENT_URI,launchIntent(context,DeepLinks.movie(url)));
            values.put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID,url);
            values.put(TvContract.PreviewPrograms.COLUMN_WEIGHT,chosen.size()-i);
            values.put(TvContract.PreviewPrograms.COLUMN_LIVE,0);
            Long id=existing.remove(url);
            if(id==null){if(resolver.insert(TvContract.PreviewPrograms.CONTENT_URI,values)==null)throw new IllegalStateException("Movie card could not be published");}else resolver.update(TvContract.buildPreviewProgramUri(id),values,null,null);
        }
        for(long id:existing.values())resolver.delete(TvContract.buildPreviewProgramUri(id),null,null);
        Log.i("KadriHome","Published "+chosen.size()+" movie cards to channel "+channel);
    }

    private static long ensureChannel(Context context) throws Exception {
        ContentResolver resolver=context.getContentResolver();
        ContentValues values=new ContentValues();values.put(TvContract.Channels.COLUMN_TYPE,TvContract.Channels.TYPE_PREVIEW);values.put(TvContract.Channels.COLUMN_DISPLAY_NAME,context.getString(R.string.app_name));values.put(TvContract.Channels.COLUMN_DESCRIPTION,"ბოლოს ნანახი და შემთხვევით შერჩეული ფილმები ქართულად");values.put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,CHANNEL_KEY);values.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,launchIntent(context,DeepLinks.HOME));
        long existing=-1;
        try(Cursor cursor=resolver.query(TvContract.Channels.CONTENT_URI,new String[]{"_id",TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,TvContract.Channels.COLUMN_PACKAGE_NAME},null,null,null)){
            if(cursor!=null)while(cursor.moveToNext())if(CHANNEL_KEY.equals(cursor.getString(1))&&context.getPackageName().equals(cursor.getString(2))){existing=cursor.getLong(0);break;}
        }
        if(existing>=0){resolver.update(TvContract.buildChannelUri(existing),values,null,null);return existing;}
        Uri uri=resolver.insert(TvContract.Channels.CONTENT_URI,values);if(uri==null)throw new IllegalStateException("TV home channel could not be created");long id=ContentUris.parseId(uri);
        Bitmap bitmap=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xffc6f36b);Drawable logo=context.getDrawable(R.drawable.ic_launcher);logo.setBounds(0,0,160,160);logo.draw(canvas);
        try(OutputStream out=resolver.openOutputStream(TvContract.buildChannelLogoUri(id))){if(out==null)throw new IllegalStateException("Channel logo unavailable");bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
        TvContract.requestChannelBrowsable(context,id);
        return id;
    }
    static String launchIntent(Context context,String data){return new Intent(Intent.ACTION_VIEW,Uri.parse(data),context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).toUri(Intent.URI_INTENT_SCHEME);}
    static synchronized void dismiss(Context context,long program){
        if(Build.VERSION.SDK_INT<26||program<0)return;
        Uri uri=TvContract.buildPreviewProgramUri(program);
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID},null,null,null)){
            if(cursor==null||!cursor.moveToFirst())return;
            SharedPreferences prefs=context.getSharedPreferences("tvhome",0);Set<String> hidden=new HashSet<>(prefs.getStringSet("hidden",Collections.emptySet()));hidden.add(cursor.getString(0));prefs.edit().putStringSet("hidden",hidden).apply();context.getContentResolver().delete(uri,null,null);
        }catch(Exception error){Log.w("KadriHome","Could not dismiss card",error);}
    }
}
