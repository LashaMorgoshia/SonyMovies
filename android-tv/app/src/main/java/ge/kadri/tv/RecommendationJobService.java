package ge.kadri.tv;

import android.app.job.*;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;

public class RecommendationJobService extends JobService {
    private final ConcurrentHashMap<Integer,Thread> workers=new ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters params){
        Thread worker=new Thread(()->{boolean retry=false;try{HomeRecommendations.refresh(this);}catch(Exception error){retry=true;Log.w("KadriHome","Recommendation refresh failed",error);}finally{if(workers.remove(params.getJobId(),Thread.currentThread()))jobFinished(params,retry);}},"kadri-home-refresh");workers.put(params.getJobId(),worker);worker.start();return true;
    }
    @Override public boolean onStopJob(JobParameters params){Thread worker=workers.remove(params.getJobId());if(worker!=null)worker.interrupt();return true;}
}
