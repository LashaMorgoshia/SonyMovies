package ge.kadri.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
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
    private LinearLayout root, body;
    private TextView heading, message;
    private ScrollView scroll;
    private JSONObject current;
    private JSONArray homeItems=new JSONArray();
    private String query="";
    private int generation=0;
    private boolean details=false;
    private View returnFocus;

    @Override public void onCreate(Bundle state) { super.onCreate(state); window(); shell(); loadCatalog(""); }
    private void window(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    private GradientDrawable shape(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(8));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
    private void focusable(View v){v.setFocusable(true);v.setBackground(shape(PANEL,0));v.setOnFocusChangeListener((view,focus)->{view.setBackground(shape(focus?0xff293321:PANEL,focus?GREEN:0));view.animate().scaleX(focus?1.025f:1).scaleY(focus?1.025f:1).setDuration(130).start();});}
    private TextView button(String label,Runnable action){TextView v=text(label,15,WHITE);v.setPadding(dp(18),dp(13),dp(18),dp(13));v.setGravity(Gravity.CENTER);focusable(v);v.setOnClickListener(w->action.run());return v;}
    private void shell(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(42),dp(26),dp(42),dp(16));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("▶  კადრი TV",25,GREEN);header.addView(logo,new LinearLayout.LayoutParams(0,dp(55),1));
        TextView home=button("მთავარი",()->loadCatalog("")), recent=button("ბოლოს გახსნილი",this::showRecent), search=button("⌕  ძებნა / ბმული",this::search);
        for(TextView v:new TextView[]{home,recent,search}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMargins(dp(8),0,0,0);header.addView(v,p);} root.addView(header);
        heading=text("შენი კინო, დიდ ეკრანზე.",26,WHITE);heading.setPadding(0,dp(24),0,dp(8));root.addView(heading);
        message=text("",13,MUTED);message.setPadding(0,0,0,dp(14));root.addView(message);
        scroll=new ScrollView(this);scroll.setClipChildren(false);scroll.setClipToPadding(false);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(5),dp(6),dp(5),dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=text("MYKADRI  /  არაოფიციალური პირადი აპი                           პულტი: ისრები • OK • უკან",10,MUTED);footer.setPadding(0,dp(10),0,0);root.addView(footer);
    }
    private void loadCatalog(String term){
        details=false;current=null;query=term;heading.setText(term.isEmpty()?"შენი კინო, დიდ ეკრანზე.":"ძიება: "+term);message.setText("ფილმები იტვირთება…");body.removeAllViews();int g=++generation;
        io.execute(()->{try{JSONArray list=Repository.catalog(term);runOnUiThread(()->{if(g!=generation)return;homeItems=list;renderCatalog(list);});}catch(Exception e){runOnUiThread(()->{if(g!=generation)return;message.setText("საიტს ვერ დავუკავშირდი. შეამოწმე ინტერნეტი და სცადე ხელახლა.");body.addView(button("ხელახლა ცდა",()->loadCatalog(term)));body.addView(button("ვიჟენქვესთის გახსნა",()->open(Repository.EXAMPLE)));});}});
    }
    private void renderCatalog(JSONArray items){
        details=false;body.removeAllViews();message.setText(items.length()==0?"ფილმი ვერ მოიძებნა. სცადე სხვა სახელი ან MyKadri-ის ბმული.":"აირჩიე ფილმი ან სერიალი პულტით");
        int available=getResources().getDisplayMetrics().widthPixels-dp(100), cols=5, width=available/cols;
        for(int i=0;i<items.length();i+=cols){LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setPadding(0,dp(4),0,dp(18));body.addView(row);
            for(int j=i;j<Math.min(i+cols,items.length());j++){
                JSONObject item=items.optJSONObject(j);if(item==null)continue;
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(7),dp(7),dp(7),dp(9));focusable(card);
                ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackgroundColor(PANEL);card.addView(cover,new LinearLayout.LayoutParams(-1,Math.round((width-dp(28))*1.4f)));loadImage(cover,item.optString("image"));
                TextView title=text(item.optString("title"),12,WHITE);title.setMaxLines(2);title.setMinLines(2);title.setPadding(dp(3),dp(10),dp(3),0);card.addView(title);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width-dp(8),-2);p.setMargins(0,0,dp(8),0);row.addView(card,p);
                card.setOnClickListener(v->{returnFocus=card;open(item.optString("url"));});if(j==0)card.requestFocus();
            }
        }scroll.scrollTo(0,0);
    }
    private void search(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setTextSize(18);input.setHint("ფილმის სახელი ან https://mykadri.tv/…");input.setPadding(dp(20),dp(16),dp(20),dp(16));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("ძებნა ან ბმულის გახსნა").setView(input).setPositiveButton("გახსნა",(d,w)->{String s=input.getText().toString().trim();if(s.startsWith("https://"))open(s);else loadCatalog(s);}).setNegativeButton("დახურვა",null).create();dialog.show();input.requestFocus();
    }
    private void open(String url){
        try{Repository.pageUrl(url);}catch(Exception e){toast(e.getMessage());return;}
        message.setText("ფილმი იტვირთება…");int g=++generation;
        io.execute(()->{try{JSONObject movie=Repository.movie(url);runOnUiThread(()->{if(g!=generation)return;current=movie;details=true;saveRecent(movie);showMovie();});}catch(Exception e){runOnUiThread(()->{if(g==generation){message.setText("ფილმის ჩატვირთვა ვერ მოხერხდა. "+e.getMessage());body.addView(button("ხელახლა ცდა",()->open(url)));}});}});
    }
    private void showMovie(){
        if(current==null)return;body.removeAllViews();heading.setText(current.optString("title"));message.setText(current.optString("englishTitle"));
        LinearLayout top=new LinearLayout(this);top.setPadding(0,0,0,dp(20));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);top.addView(image,new LinearLayout.LayoutParams(dp(230),dp(135)));loadImage(image,current.optString("image"));
        LinearLayout intro=new LinearLayout(this);intro.setOrientation(LinearLayout.VERTICAL);intro.setPadding(dp(24),0,0,0);top.addView(intro,new LinearLayout.LayoutParams(0,-2,1));
        TextView description=text(current.optString("description").split("\\s\\d{4}\\s")[0],14,MUTED);description.setMaxLines(4);description.setLineSpacing(dp(4),1);intro.addView(description);
        TextView back=button("← უკან",()->{details=false;heading.setText(query.isEmpty()?"შენი კინო, დიდ ეკრანზე.":"ძიება: "+query);renderCatalog(homeItems);});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.topMargin=dp(14);intro.addView(back,bp);body.addView(top);
        JSONArray sources=current.optJSONArray("sources");
        if(sources==null || sources.length()==0){body.addView(text("პირდაპირი ქართული ვიდეონაკადი ვერ მოიძებნა. გარე ჩაშენებული ფლეერები ჯერ არ არის მხარდაჭერილი.",16,MUTED));back.requestFocus();return;}
        String section="";LinearLayout row=null;int count=0;boolean first=true;
        for(int i=0;i<sources.length();i++){
            JSONObject source=sources.optJSONObject(i);String group=source.optString("season")+"|"+source.optString("language");
            if(!group.equals(section)){section=group;String season=source.optString("season");TextView label=text((season.isEmpty()?"ფილმი":"სეზონი "+season)+"  /  "+source.optString("language"),16,GREEN);label.setPadding(0,dp(15),0,dp(12));body.addView(label);count=0;row=null;}
            if(count%5==0){row=new LinearLayout(this);row.setPadding(0,0,0,dp(10));body.addView(row);}
            String name=source.optString("episode");if(name.isEmpty())name="▶  ყურება · "+source.optString("quality","ავტომატური");
            int index=i;TextView play=button(name,()->launch(index));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1);p.setMargins(0,0,dp(10),0);row.addView(play,p);count++;
            if(first){play.requestFocus();first=false;}
        }scroll.scrollTo(0,0);
    }
    private void launch(int sourceIndex){
        try{
            JSONObject source=current.getJSONArray("sources").getJSONObject(sourceIndex);String key=source.getString("url");long position=getSharedPreferences("progress",0).getLong(key,0);
            Runnable start=()->{Intent intent=new Intent(this,PlayerActivity.class);intent.putExtra("movie",current.toString());intent.putExtra("index",sourceIndex);startActivity(intent);};
            if(position>20000)new AlertDialog.Builder(this).setTitle("გააგრძელებ ყურებას?").setMessage("შენახული დრო: "+position/60000+" წუთი").setPositiveButton("გაგრძელება",(d,w)->start.run()).setNegativeButton("თავიდან",(d,w)->{getSharedPreferences("progress",0).edit().remove(key).apply();start.run();}).show();else start.run();
        }catch(Exception e){toast("ვიდეოს გახსნა ვერ მოხერხდა.");}
    }
    private void saveRecent(JSONObject item){try{JSONArray old=new JSONArray(getSharedPreferences("library",0).getString("recent","[]")),next=new JSONArray();next.put(new JSONObject().put("title",item.optString("title")).put("url",item.optString("url")).put("image",item.optString("image")));for(int i=0;i<old.length()&&next.length()<30;i++)if(!old.getJSONObject(i).optString("url").equals(item.optString("url")))next.put(old.getJSONObject(i));getSharedPreferences("library",0).edit().putString("recent",next.toString()).apply();}catch(Exception ignored){}}
    private void showRecent(){++generation;query="";heading.setText("ბოლოს გახსნილი");try{homeItems=new JSONArray(getSharedPreferences("library",0).getString("recent","[]"));renderCatalog(homeItems);}catch(Exception ignored){}}
    private void loadImage(ImageView view,String url){
        view.setTag(url);if(!url.startsWith("https://"))return;Bitmap cached=images.get(url);if(cached!=null){view.setImageBitmap(cached);return;}
        imageIo.execute(()->{try{HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(10000);try(java.io.InputStream stream=connection.getInputStream()){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;Bitmap b=BitmapFactory.decodeStream(stream,null,options);if(b!=null){images.put(url,b);runOnUiThread(()->{if(url.equals(view.getTag()))view.setImageBitmap(b);});}}finally{connection.disconnect();}}catch(Exception ignored){}});
    }
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){if(details){++generation;details=false;heading.setText(query.isEmpty()?"შენი კინო, დიდ ეკრანზე.":"ძიება: "+query);renderCatalog(homeItems);}else new AlertDialog.Builder(this).setTitle("დახურავ კადრი TV-ს?").setPositiveButton("დახურვა",(d,w)->finish()).setNegativeButton("დარჩენა",null).show();}
    @Override protected void onDestroy(){++generation;io.shutdownNow();imageIo.shutdownNow();super.onDestroy();}
}
