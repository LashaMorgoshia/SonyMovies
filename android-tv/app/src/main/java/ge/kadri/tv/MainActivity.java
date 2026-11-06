package ge.kadri.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BG=0xff101114, PANEL=0xff1c2026, GREEN=0xffc6f36b, WHITE=0xfff5f5f1, MUTED=0xff959ba5;
    private final ExecutorService io=Executors.newFixedThreadPool(5);
    private final ExecutorService imageIo=Executors.newFixedThreadPool(4);
    private final android.util.LruCache<String,Bitmap> images=new android.util.LruCache<>(18*1024) { protected int sizeOf(String k,Bitmap v){return v.getByteCount()/1024;} };
    private LinearLayout root, page, body, recentSection;
    private EditText searchInput;
    private View firstRecentCard;
    private TextView heading, message;
    private ScrollView scroll;
    private JSONObject current;
    private JSONArray homeItems=new JSONArray();
    private String query="";
    private int generation=0;
    private boolean details=false;
    private View returnFocus;

    @Override public void onCreate(Bundle state) { super.onCreate(state); window(); shell(); HomeRecommendations.requestRefresh(this);refreshHomeRecent();if(!handleAppLink(getIntent()))loadCatalog(""); }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(!handleAppLink(intent))loadCatalog("");}
    private boolean handleAppLink(Intent intent){
        if(intent==null||intent.getData()==null||!Intent.ACTION_VIEW.equals(intent.getAction()))return false;
        try{
            if(DeepLinks.HOME.equals(intent.getData().toString())){searchInput.setText("");hideKeyboard();loadCatalog("");return true;}
            String url=DeepLinks.movieUrl(intent.getData().toString());if(url==null)return false;
            query="";homeItems=new JSONArray();returnFocus=null;searchInput.setText("");hideKeyboard();open(url);return true;
        }catch(IllegalArgumentException error){toast("ფილმის ბმული ვერ გაიხსნა.");return false;}
    }
    private void window(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private GradientDrawable shape(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(8));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
    private void focusable(View v){v.setFocusable(true);v.setBackground(shape(PANEL,0));v.setOnFocusChangeListener((view,focus)->{view.setBackground(shape(focus?0xff293321:PANEL,focus?GREEN:0));view.animate().scaleX(focus?1.025f:1).scaleY(focus?1.025f:1).setDuration(130).start();});}
    private TextView button(String label,Runnable action){TextView v=text(label,15,WHITE);v.setPadding(dp(18),dp(13),dp(18),dp(13));v.setGravity(Gravity.CENTER);focusable(v);v.setOnClickListener(w->action.run());return v;}
    private void shell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(42),dp(26),dp(42),dp(16));root.setFocusableInTouchMode(true);setContentView(root);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("▶  "+getString(R.string.app_name),25,GREEN);header.addView(logo,new LinearLayout.LayoutParams(0,dp(55),1));
        TextView home=button("მთავარი",()->{searchInput.setText("");hideKeyboard();loadCatalog("");});home.setId(View.generateViewId());
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,-2);hp.setMargins(dp(8),0,dp(12),0);header.addView(home,hp);
        LinearLayout searchBox=new LinearLayout(this);searchBox.setGravity(Gravity.CENTER_VERTICAL);searchBox.setPadding(dp(14),0,dp(12),0);searchBox.setBackground(shape(PANEL,0));
        TextView searchIcon=text("⌕",25,GREEN);searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);searchBox.addView(searchIcon);
        searchInput=new EditText(this);searchInput.setId(View.generateViewId());searchInput.setSingleLine(true);searchInput.setTextColor(WHITE);searchInput.setHintTextColor(MUTED);searchInput.setTextSize(14);searchInput.setBackgroundColor(Color.TRANSPARENT);searchInput.setPadding(dp(12),dp(10),0,dp(10));searchInput.setHint("ფილმის სახელი ან ბმული…");searchInput.setContentDescription("ფილმის ძებნა ან ბმულის გახსნა");
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);
        searchInput.setOnFocusChangeListener((v,focus)->searchBox.setBackground(shape(PANEL,focus?GREEN:0)));
        searchInput.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH||action==EditorInfo.IME_ACTION_GO||action==EditorInfo.IME_ACTION_DONE||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                if(event==null||event.getAction()==KeyEvent.ACTION_DOWN)submitSearch();return true;
            }return false;
        });
        home.setNextFocusRightId(searchInput.getId());searchInput.setNextFocusLeftId(home.getId());
        searchBox.addView(searchInput,new LinearLayout.LayoutParams(0,-1,1));header.addView(searchBox,new LinearLayout.LayoutParams(dp(350),dp(48)));root.addView(header);
        scroll=new ScrollView(this);scroll.setClipChildren(false);scroll.setClipToPadding(false);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        recentSection=new LinearLayout(this);recentSection.setOrientation(LinearLayout.VERTICAL);recentSection.setPadding(dp(5),dp(20),dp(5),0);page.addView(recentSection);
        heading=text("შენი კინო, დიდ ეკრანზე.",26,WHITE);heading.setPadding(0,dp(24),0,dp(8));page.addView(heading);
        message=text("",13,MUTED);message.setPadding(0,0,0,dp(14));page.addView(message);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(5),dp(6),dp(5),dp(20));page.addView(body);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=text("MYKADRI  /  არაოფიციალური პირადი აპი                           პულტი: ისრები • OK • უკან",10,MUTED);footer.setPadding(0,dp(10),0,0);root.addView(footer);
        root.requestFocus();
    }
    private void loadCatalog(String term){
        details=false;current=null;returnFocus=null;query=term;renderRecentSection();heading.setText(term.isEmpty()?"შენი კინო, დიდ ეკრანზე.":"ძიება: "+term);message.setText("ფილმები იტვირთება…");body.removeAllViews();scroll.scrollTo(0,0);int g=++generation;
        io.execute(()->{try{JSONArray list=Repository.catalog(term);if(term.isEmpty())try{HomeRecommendations.sync(this,list);}catch(Exception e){android.util.Log.w("KadriHome","Could not update home cards",e);}runOnUiThread(()->{if(g!=generation)return;homeItems=list;renderCatalog(list);});}catch(Exception e){runOnUiThread(()->{if(g!=generation)return;message.setText("საიტს ვერ დავუკავშირდი. შეამოწმე ინტერნეტი და სცადე ხელახლა.");body.addView(button("ხელახლა ცდა",()->loadCatalog(term)));body.addView(button("ვიჟენქვესთის გახსნა",()->open(Repository.EXAMPLE)));});}});
    }
    private void renderCatalog(JSONArray items){
        details=false;body.removeAllViews();renderRecentSection();heading.setText(query.isEmpty()?"შენი კინო, დიდ ეკრანზე.":"ძიება: "+query);message.setText(items.length()==0?"ფილმი ვერ მოიძებნა. სცადე სხვა სახელი ან MyKadri-ის ბმული.":"აირჩიე ფილმი ან სერიალი პულტით");
        int available=getResources().getDisplayMetrics().widthPixels-dp(100), cols=5, width=available/cols;
        View firstCatalogCard=null;
        for(int i=0;i<items.length();i+=cols){LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setPadding(0,dp(4),0,dp(18));body.addView(row);
            for(int j=i;j<Math.min(i+cols,items.length());j++){
                JSONObject item=items.optJSONObject(j);if(item==null)continue;
                LinearLayout card=movieCard(item,width-dp(8),false);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width-dp(8),-2);p.setMargins(0,0,dp(8),0);row.addView(card,p);
                if(firstCatalogCard==null)firstCatalogCard=card;
            }
        }
        View preferred=returnFocus==null?null:page.findViewWithTag(returnFocus.getTag());
        final View target=preferred!=null?preferred:firstRecentCard!=null?firstRecentCard:firstCatalogCard;
        final boolean restore=preferred!=null;
        if(target!=null){searchInput.setNextFocusDownId(target.getId());scroll.post(()->{if(!details&&!searchInput.hasFocus()&&target.isAttachedToWindow()){target.requestFocus();if(!restore)scroll.scrollTo(0,0);}});}
        if(!restore)scroll.scrollTo(0,0);
    }
    private LinearLayout movieCard(JSONObject item,int width,boolean recent){
        LinearLayout card=new LinearLayout(this);card.setId(View.generateViewId());card.setTag((recent?"recent:":"catalog:")+item.optString("url"));card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(7),dp(7),dp(7),dp(9));focusable(card);
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackgroundColor(PANEL);int height=recent?Math.round((width-dp(14))*9f/16f):Math.round((width-dp(20))*1.4f);card.addView(cover,new LinearLayout.LayoutParams(-1,height));loadImage(cover,item.optString("image"));
        TextView title=text(item.optString("title"),12,WHITE);title.setMaxLines(2);title.setMinLines(2);title.setPadding(dp(3),dp(9),dp(3),0);card.addView(title);
        card.setContentDescription(item.optString("title"));card.setOnClickListener(v->{returnFocus=card;hideKeyboard();open(item.optString("url"));});return card;
    }
    private void renderRecentSection(){
        recentSection.removeAllViews();firstRecentCard=null;recentSection.setVisibility(View.GONE);if(!query.isEmpty()||details)return;
        JSONArray items;try{items=new JSONArray(getSharedPreferences("library",0).getString("recent","[]"));}catch(JSONException e){return;}
        if(items.length()==0)return;
        TextView label=text("ბოლოს ნანახი",18,WHITE);label.setPadding(0,0,0,dp(12));recentSection.addView(label);
        HorizontalScrollView rail=new HorizontalScrollView(this);rail.setHorizontalScrollBarEnabled(false);rail.setClipChildren(false);rail.setClipToPadding(false);rail.setPadding(dp(3),dp(4),dp(3),dp(8));
        LinearLayout row=new LinearLayout(this);row.setClipChildren(false);rail.addView(row);recentSection.addView(rail);
        int width=(getResources().getDisplayMetrics().widthPixels-dp(100))/5-dp(8);
        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);if(item==null||item.optString("url").isEmpty())continue;
            LinearLayout card=movieCard(item,width,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,-2);p.setMargins(0,0,dp(8),0);row.addView(card,p);if(firstRecentCard==null)firstRecentCard=card;
        }
        if(firstRecentCard!=null)recentSection.setVisibility(View.VISIBLE);
    }
    private void hideKeyboard(){InputMethodManager ime=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(searchInput.getWindowToken(),0);searchInput.clearFocus();root.requestFocus();}
    private void submitSearch(){
        String value=searchInput.getText().toString().trim();hideKeyboard();if(value.startsWith("https://")||value.startsWith("http://"))open(value);else loadCatalog(value);
    }
    private void open(String url){
        try{Repository.pageUrl(url);}catch(Exception e){toast(e.getMessage());return;}
        message.setText("ფილმი იტვირთება…");int g=++generation;
        io.execute(()->{try{JSONObject movie=Repository.movie(url);runOnUiThread(()->{if(g!=generation)return;current=movie;details=true;saveRecent(movie);showMovie();});}catch(Exception e){runOnUiThread(()->{if(g==generation){message.setText("ფილმის ჩატვირთვა ვერ მოხერხდა. "+e.getMessage());body.addView(button("ხელახლა ცდა",()->open(url)));}});}});
    }
    private void showMovie(){
        if(current==null)return;recentSection.setVisibility(View.GONE);body.removeAllViews();heading.setText(current.optString("title"));message.setText(current.optString("englishTitle"));
        LinearLayout top=new LinearLayout(this);top.setPadding(0,0,0,dp(20));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);top.addView(image,new LinearLayout.LayoutParams(dp(230),dp(135)));loadImage(image,current.optString("image"));
        LinearLayout intro=new LinearLayout(this);intro.setOrientation(LinearLayout.VERTICAL);intro.setPadding(dp(24),0,0,0);top.addView(intro,new LinearLayout.LayoutParams(0,-2,1));
        TextView description=text(current.optString("description").split("\\s\\d{4}\\s")[0],14,MUTED);description.setMaxLines(4);description.setLineSpacing(dp(4),1);intro.addView(description);
        TextView back=button("← უკან",this::backToCatalog);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.topMargin=dp(14);intro.addView(back,bp);body.addView(top);
        JSONArray sources=current.optJSONArray("sources");
        if(sources==null || sources.length()==0){body.addView(text("პირდაპირი ქართული ვიდეონაკადი ვერ მოიძებნა. გარე ჩაშენებული ფლეერები ჯერ არ არის მხარდაჭერილი.",16,MUTED));back.requestFocus();return;}
        TextView quality=button("ხარისხი: "+VideoQuality.label(VideoQuality.preferred(getSharedPreferences("playback",0)))+"  ▾",()->{});
        quality.setOnClickListener(v->showMovieQuality(quality,sources.optJSONObject(0)));LinearLayout.LayoutParams qualityParams=new LinearLayout.LayoutParams(-2,-2);qualityParams.setMargins(0,0,0,dp(8));body.addView(quality,qualityParams);
        String section="";LinearLayout row=null;int count=0;boolean first=true;
        for(int i=0;i<sources.length();i++){
            JSONObject source=sources.optJSONObject(i);String group=source.optString("season")+"|"+source.optString("language");
            if(!group.equals(section)){section=group;String season=source.optString("season");TextView label=text((season.isEmpty()?"ფილმი":"სეზონი "+season)+"  /  "+source.optString("language"),16,GREEN);label.setPadding(0,dp(15),0,dp(12));body.addView(label);count=0;row=null;}
            if(count%5==0){row=new LinearLayout(this);row.setPadding(0,0,0,dp(10));body.addView(row);}
            String name=source.optString("episode");if(name.isEmpty())name=source.optString("url").contains(".m3u8")?"▶  ყურება":"▶  ყურება · "+source.optString("quality","ორიგინალი");
            int index=i;TextView play=button(name,()->launch(index));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1);p.setMargins(0,0,dp(10),0);row.addView(play,p);count++;
            if(first){play.requestFocus();first=false;}
        }scroll.scrollTo(0,0);
    }
    private void showMovieQuality(TextView button,JSONObject source){
        if(source==null||current==null)return;
        final int g=generation;final String referer=current.optString("url");button.setEnabled(false);button.setText("ხარისხები იტვირთება…");
        io.execute(()->{try{
            List<Integer> heights=VideoQuality.available(source.optString("url"),referer);
            runOnUiThread(()->{if(g!=generation||!details)return;button.setEnabled(true);button.requestFocus();button.setText("ხარისხი: "+VideoQuality.label(VideoQuality.preferred(getSharedPreferences("playback",0)))+"  ▾");
                ArrayList<String> labels=new ArrayList<>();labels.add(VideoQuality.label(0));for(int h:heights)labels.add(h+"p");int preferred=VideoQuality.preferred(getSharedPreferences("playback",0));int selected=heights.indexOf(preferred)+1;
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle("ვიდეოს ხარისხი").setSingleChoiceItems(labels.toArray(new String[0]),selected,(d,w)->{int height=w==0?0:heights.get(w-1);VideoQuality.save(getSharedPreferences("playback",0),height);button.setText("ხარისხი: "+VideoQuality.label(height)+"  ▾");d.dismiss();}).setNegativeButton("დახურვა",null).create();
                dialog.setOnDismissListener(d->{if(g==generation&&details)button.requestFocus();});dialog.show();
            });
        }catch(Exception error){runOnUiThread(()->{if(g!=generation||!details)return;button.setEnabled(true);button.requestFocus();button.setText("ხარისხი: "+VideoQuality.label(VideoQuality.preferred(getSharedPreferences("playback",0)))+"  ▾");toast("ხარისხების სია ვერ ჩაიტვირთა. არჩევა ფლეერშიც შეგიძლია.");});}});
    }
    private void launch(int sourceIndex){
        try{
            JSONObject source=current.getJSONArray("sources").getJSONObject(sourceIndex);String key=source.getString("url");long position=getSharedPreferences("progress",0).getLong(key,0);
            Runnable start=()->{Intent intent=new Intent(this,PlayerActivity.class);intent.putExtra("movie",current.toString());intent.putExtra("index",sourceIndex);startActivity(intent);};
            if(position>20000)new AlertDialog.Builder(this).setTitle("გააგრძელებ ყურებას?").setMessage("შენახული დრო: "+position/60000+" წუთი").setPositiveButton("გაგრძელება",(d,w)->start.run()).setNegativeButton("თავიდან",(d,w)->{getSharedPreferences("progress",0).edit().remove(key).apply();start.run();}).show();else start.run();
        }catch(Exception e){toast("ვიდეოს გახსნა ვერ მოხერხდა.");}
    }
    private void saveRecent(JSONObject item){try{JSONArray old=new JSONArray(getSharedPreferences("library",0).getString("recent","[]")),next=new JSONArray();next.put(new JSONObject().put("title",item.optString("title")).put("url",item.optString("url")).put("image",item.optString("image")));for(int i=0;i<old.length()&&next.length()<30;i++)if(!old.getJSONObject(i).optString("url").equals(item.optString("url")))next.put(old.getJSONObject(i));getSharedPreferences("library",0).edit().putString("recent",next.toString()).apply();refreshHomeRecent();}catch(Exception ignored){}}
    private void refreshHomeRecent(){android.content.Context app=getApplicationContext();io.execute(()->{try{HomeRecommendations.syncRecent(app);}catch(Exception error){android.util.Log.w("KadriHome","Could not update recent home cards",error);}});}
    private void backToCatalog(){++generation;details=false;searchInput.setText(query);if(homeItems.length()==0)loadCatalog(query);else renderCatalog(homeItems);}
    private void loadImage(ImageView view,String url){
        view.setTag(url);if(!url.startsWith("https://"))return;Bitmap cached=images.get(url);if(cached!=null){view.setImageBitmap(cached);return;}
        imageIo.execute(()->{try{HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(10000);try(java.io.InputStream stream=connection.getInputStream()){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap b=BitmapFactory.decodeStream(stream,null,options);if(b!=null){images.put(url,b);runOnUiThread(()->{if(url.equals(view.getTag()))view.setImageBitmap(b);});}}finally{connection.disconnect();}}catch(Exception ignored){}});
    }
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(details)backToCatalog();else if(!query.isEmpty()){searchInput.setText("");loadCatalog("");}else new AlertDialog.Builder(this).setTitle("დახურავ "+getString(R.string.app_name)+"-ს?").setPositiveButton("დახურვა",(d,w)->finish()).setNegativeButton("დარჩენა",null).show();}
    @Override protected void onDestroy(){++generation;io.shutdownNow();imageIo.shutdownNow();super.onDestroy();}
}
