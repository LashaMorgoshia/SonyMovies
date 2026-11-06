package ge.kadri.tv;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
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
    private TextView qualityButton, bufferingText;
    private ExoPlayer player;
    private MediaSession session;
    private AlertDialog recoveryDialog;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BufferingWatchdog watchdog=new BufferingWatchdog();
    private final ArrayList<MediaItem> playlist=new ArrayList<>();
    private int index=0;
    private int qualityHeight=0, saveTicks=0;
    private final Set<String> qualityFallbacks=new HashSet<>();
    private long position=0;
    private boolean shouldPlay=true;
    private String referer;
    private final Runnable save=new Runnable(){public void run(){
        if(player==null)return;
        if(++saveTicks%3==0)savePosition();
        boolean buffering=player.getPlaybackState()==Player.STATE_BUFFERING;
        bufferingText.setVisibility(buffering?View.VISIBLE:View.GONE);
        if(buffering)bufferingText.setText("იტვირთება · მზადაა "+player.getTotalBufferedDuration()/1000+" წმ");
        if(watchdog.shouldOffer(SystemClock.elapsedRealtime(),buffering,player.getPlayWhenReady()))showRecovery(false);
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        view=new PlayerView(this);view.setKeepScreenOn(true);view.setShowNextButton(true);view.setShowPreviousButton(true);view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);view.setControllerAutoShow(true);
        FrameLayout root=new FrameLayout(this);root.addView(view,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        qualityHeight=VideoQuality.preferred(getSharedPreferences("playback",0));
        qualityButton=new TextView(this);qualityButton.setId(View.generateViewId());qualityButton.setTextSize(14);qualityButton.setPadding(dp(16),dp(11),dp(16),dp(11));qualityButton.setFocusable(true);
        qualityButton.setOnClickListener(v->showQuality());qualityButton.setOnFocusChangeListener((v,focus)->styleQuality(focus));styleQuality(false);updateQualityLabel();
        FrameLayout.LayoutParams qp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);qp.setMargins(dp(24),dp(24),dp(24),0);root.addView(qualityButton,qp);
        bufferingText=new TextView(this);bufferingText.setTextColor(0xfff5f5f1);bufferingText.setTextSize(14);bufferingText.setPadding(dp(16),dp(11),dp(16),dp(11));bufferingText.setBackgroundColor(0xbb101114);bufferingText.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);bp.setMargins(dp(24),dp(24),dp(24),0);root.addView(bufferingText,bp);
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener)visibility->qualityButton.setVisibility(visibility));
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
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 KadriTV/1.0").setDefaultRequestProperties(Collections.singletonMap("Referer",referer)).setConnectTimeoutMs(10000).setReadTimeoutMs(12000);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(()->new VideoDataSource(http.createDataSource())).setLoadErrorHandlingPolicy(new VideoDataSource.ErrorPolicy())).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        applyQuality();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);view.setPlayer(player);session=new MediaSession.Builder(this,player).build();
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                android.util.Log.e("KadriPlayback",error.getErrorCodeName(),error);
                Throwable cause=error;boolean empty=false;while(cause!=null){if(cause instanceof VideoDataSource.EmptySegmentException)empty=true;cause=cause.getCause();}
                String key=(player.getCurrentMediaItem()==null?"":player.getCurrentMediaItem().mediaId)+"|"+qualityHeight;
                if(empty&&qualityHeight>0&&qualityFallbacks.add(key)){qualityHeight=0;applyQuality();updateQualityLabel();Toast.makeText(PlayerActivity.this,"ამ ხარისხის მონაკვეთი მიუწვდომელია. ავტომატური რეჟიმით ვცდი.",Toast.LENGTH_LONG).show();restartAtCurrentPosition();return;}
                showRecovery(true);
            }
            @Override public void onTracksChanged(Tracks tracks){
                List<Integer> available=availableHeights();
                if(qualityHeight>0&&!available.isEmpty()&&!available.contains(qualityHeight)){qualityHeight=0;Toast.makeText(PlayerActivity.this,"არჩეული ხარისხი ამ ვიდეოზე არ არის. ავტომატურად ვარჩევ.",Toast.LENGTH_LONG).show();}
                applyQuality();updateQualityLabel();
            }
            @Override public void onVideoSizeChanged(VideoSize size){updateQualityLabel();}
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK)watchdog.reset();}
            @Override public void onPlaybackStateChanged(int state){
                if(state!=Player.STATE_BUFFERING)watchdog.reset();
                if(state==Player.STATE_READY&&recoveryDialog!=null){recoveryDialog.dismiss();recoveryDialog=null;}
                if(state==Player.STATE_ENDED && player.getCurrentMediaItem()!=null)getSharedPreferences("progress",0).edit().remove(player.getCurrentMediaItem().mediaId).apply();
            }
        });
        player.setMediaItems(playlist,index,position);player.prepare();player.setPlayWhenReady(shouldPlay);view.requestFocus();handler.postDelayed(save,1000);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void styleQuality(boolean focus){GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(7));bg.setColor(focus?0xffc6f36b:0xdd1c2026);qualityButton.setBackground(bg);qualityButton.setTextColor(focus?0xff101114:0xffc6f36b);}
    private void updateQualityLabel(){int actual=player!=null&&player.getVideoFormat()!=null?player.getVideoFormat().height:0;qualityButton.setText("ხარისხი: "+(qualityHeight>0?qualityHeight+"p":actual>0?"ავტო · "+actual+"p":"ავტო · 1080p-მდე")+"  ▾");}
    private List<Integer> availableHeights(){
        TreeSet<Integer> heights=new TreeSet<>();if(player!=null)for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_VIDEO)for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)&&group.getTrackFormat(i).height>0)heights.add(group.getTrackFormat(i).height);
        return new ArrayList<>(heights);
    }
    private void applyQuality(){
        if(player==null)return;
        TrackSelectionParameters.Builder p=player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setForceHighestSupportedBitrate(false).setForceLowestBitrate(false).setMaxVideoBitrate(Integer.MAX_VALUE).setMinVideoSize(0,0);
        if(qualityHeight==0)p.setMaxVideoSize(1920,1080);
        else{
            p.setMaxVideoSize(Integer.MAX_VALUE,qualityHeight);
            boolean found=false;
            for(Tracks.Group group:player.getCurrentTracks().getGroups()){
                if(group.getType()!=C.TRACK_TYPE_VIDEO)continue;
                for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)&&group.getTrackFormat(i).height==qualityHeight){p.setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),i));found=true;break;}
                if(found)break;
            }
        }
        TrackSelectionParameters parameters=p.build();if(!parameters.equals(player.getTrackSelectionParameters()))player.setTrackSelectionParameters(parameters);
    }
    private void chooseQuality(int height){qualityHeight=height;VideoQuality.save(getSharedPreferences("playback",0),height);updateQualityLabel();applyQuality();restartAtCurrentPosition();}
    private void showQuality(){
        if(player==null||isFinishing())return;
        List<Integer> heights=availableHeights();ArrayList<String> labels=new ArrayList<>();labels.add(VideoQuality.label(0));for(int h:heights)labels.add(h+"p");int selected=heights.indexOf(qualityHeight)+1;
        new AlertDialog.Builder(this).setTitle("ვიდეოს ხარისხი").setSingleChoiceItems(labels.toArray(new String[0]),selected,(d,w)->{d.dismiss();chooseQuality(w==0?0:heights.get(w-1));}).setNegativeButton("დახურვა",null).show();
    }
    private void restartAtCurrentPosition(){
        if(player==null)return;
        index=player.getCurrentMediaItemIndex();position=player.getCurrentPosition();shouldPlay=player.getPlayWhenReady();
        watchdog.reset();player.stop();player.setMediaItems(playlist,index,position);player.prepare();player.setPlayWhenReady(shouldPlay);view.requestFocus();
    }
    private void showRecovery(boolean error){
        if(player==null||isFinishing()||(recoveryDialog!=null&&recoveryDialog.isShowing()))return;
        recoveryDialog=new AlertDialog.Builder(this).setTitle(error?"ვიდეო ვერ ჩაიტვირთა":"ჩატვირთვა შეყოვნდა")
            .setMessage("სერვერი ვიდეოს დროულად ვერ გვაწვდის. შეგიძლია იმავე ადგილიდან დაბალი ხარისხით სცადო ან ჩატვირთვა გაიმეორო.")
            .setPositiveButton("720p-ით ცდა",(d,w)->{recoveryDialog=null;chooseQuality(720);})
            .setNeutralButton("ხელახლა",(d,w)->{recoveryDialog=null;restartAtCurrentPosition();})
            .setNegativeButton("უკან",(d,w)->finish()).create();
        recoveryDialog.setOnDismissListener(d->recoveryDialog=null);recoveryDialog.show();
    }
    private void savePosition(){if(player!=null&&player.getCurrentMediaItem()!=null){index=player.getCurrentMediaItemIndex();position=player.getCurrentPosition();shouldPlay=player.getPlayWhenReady();String key=player.getCurrentMediaItem().mediaId;long duration=player.getDuration();if(duration>0&&position>=duration-10000)getSharedPreferences("progress",0).edit().remove(key).apply();else getSharedPreferences("progress",0).edit().putLong(key,position).apply();}}
    private void release(){handler.removeCallbacks(save);watchdog.reset();if(recoveryDialog!=null){recoveryDialog.dismiss();recoveryDialog=null;}if(player!=null){savePosition();if(session!=null){session.release();session=null;}view.setPlayer(null);player.release();player=null;}}
    @Override protected void onStart(){super.onStart();initialize();}
    @Override protected void onStop(){release();super.onStop();}
    @Override protected void onDestroy(){release();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){savePosition();state.putInt("index",index);state.putLong("position",position);state.putBoolean("playing",shouldPlay);super.onSaveInstanceState(state);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)showQuality();return true;}if(qualityButton.hasFocus())return super.dispatchKeyEvent(event);return view.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(view.isControllerFullyVisible())view.hideController();else super.onBackPressed();}
}
