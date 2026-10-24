package tw.sleeptimer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager target: works even if the app process was killed. */
class PowerOffReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent?) { Timer.fire(c) }
}
