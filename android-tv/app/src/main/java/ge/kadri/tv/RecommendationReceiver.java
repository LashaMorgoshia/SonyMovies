package ge.kadri.tv;

import android.content.*;
import android.media.tv.TvContract;
import android.os.Build;

public class RecommendationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(Build.VERSION.SDK_INT<26)return;
        if(TvContract.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED.equals(intent.getAction()))HomeRecommendations.dismiss(context,intent.getLongExtra(TvContract.EXTRA_PREVIEW_PROGRAM_ID,-1));
        else if(TvContract.ACTION_INITIALIZE_PROGRAMS.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))HomeRecommendations.requestRefresh(context);
    }
}
