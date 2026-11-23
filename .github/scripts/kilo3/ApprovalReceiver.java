package hu.jarvis.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Confirms only the single approval already displayed by the running app. */
public final class ApprovalReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context,Intent intent){
    if(intent==null||!"hu.jarvis.app.APPROVE_KILO3".equals(intent.getAction()))return;
    ControlService s=ControlService.live;
    if(s!=null&&s.confirm&&s.runner!=null&&s.runner.running)s.approvePending();
  }
}
