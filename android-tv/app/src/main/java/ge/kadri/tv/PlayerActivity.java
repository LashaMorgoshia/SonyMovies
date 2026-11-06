package ge.kadri.tv;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerView;
import org.json.*;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public class PlayerActivity extends Activity {
    private PlayerView view;
    private ExoPlayer player;
    private MediaSession session;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<MediaItem> playlist=new ArrayList<>();
    private int index=0;
    private long position=0;
    private boolean shouldPlay=true;
    private String referer;
    private final Runnable save=new Runnable(){public void run(){savePosition();handler.postDelayed(this,3000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        view=new PlayerView(this);view.setKeepScreenOn(true);view.setShowNextButton(true);view.setShowPreviousButton(true);view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);view.setControllerAutoShow(true);setContentView(view);
        try{
            JSONObject movie=new JSONObject(getIntent().getStringExtra("movie"));JSONArray sources=movie.getJSONArray("sources");int chosen=getIntent().getIntExtra("index",0);JSONObject selected=sources.getJSONObject(chosen);referer=movie.getString("url");
            for(int i=0;i<sources.length();i++){
                JSONObject source=sources.getJSONObject(i);if(!source.optString("season").equals(selected.optString("season"))||!source.optString("language").equals(selected.optString("language")))continue;
                if(i==chosen)index=playlist.size();String url=source.getString("url");String title=movie.getString("title")+(source.optString("episode").isEmpty()?"":" · "+source.optString("episode"));
                playlist.add(new MediaItem.Builder().setUri(url).setMediaId(url).setMimeType(url.contains(".m3u8")?MimeTypes.APPLICATION_M3U8:MimeTypes.VIDEO_MP4).setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build()).build());
            }
            position=getSharedPreferences("progress",0).getLong(selected.getString("url"),0);
            if(state!=null){index=state.getInt("index",index);position=state.getLong("position",position);shouldPlay=state.getBoolean("playing",true);}
        }catch(Exception e){Toast.makeText(this,"ვიდეოს მონაცემები ვერ გაიხსნა.",Toast.LENGTH_LONG).show();finish();}
    }
    private void initialize(){
        if(player!=null||playlist.isEmpty())return;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 KadriTV/1.0").setDefaultRequestProperties(Collections.singletonMap("Referer",referer)).setConnectTimeoutMs(15000).setReadTimeoutMs(25000);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);view.setPlayer(player);session=new MediaSession.Builder(this,player).build();
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){if(!isFinishing())new AlertDialog.Builder(PlayerActivity.this).setTitle("ვიდეო ვერ ჩაიტვირთა").setMessage("შეამოწმე ინტერნეტი ან სცადე სხვა სერია. წყარო შეიძლება დროებით მიუწვდომელი იყოს.\n"+error.getErrorCodeName()).setPositiveButton("ხელახლა",(d,w)->{player.prepare();player.play();}).setNegativeButton("უკან",(d,w)->finish()).show();}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED && player.getCurrentMediaItem()!=null)getSharedPreferences("progress",0).edit().remove(player.getCurrentMediaItem().mediaId).apply();}
        });
        player.setMediaItems(playlist,index,position);player.prepare();player.setPlayWhenReady(shouldPlay);view.requestFocus();handler.postDelayed(save,3000);
    }
    private void savePosition(){if(player!=null&&player.getCurrentMediaItem()!=null){index=player.getCurrentMediaItemIndex();position=player.getCurrentPosition();shouldPlay=player.getPlayWhenReady();String key=player.getCurrentMediaItem().mediaId;long duration=player.getDuration();if(duration>0&&position>=duration-10000)getSharedPreferences("progress",0).edit().remove(key).apply();else getSharedPreferences("progress",0).edit().putLong(key,position).apply();}}
    private void release(){handler.removeCallbacks(save);if(player!=null){savePosition();if(session!=null){session.release();session=null;}view.setPlayer(null);player.release();player=null;}}
    @Override protected void onStart(){super.onStart();initialize();}
    @Override protected void onStop(){release();super.onStop();}
    @Override protected void onDestroy(){release();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){savePosition();state.putInt("index",index);state.putLong("position",position);state.putBoolean("playing",shouldPlay);super.onSaveInstanceState(state);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return view.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(view.isControllerFullyVisible())view.hideController();else super.onBackPressed();}
}
