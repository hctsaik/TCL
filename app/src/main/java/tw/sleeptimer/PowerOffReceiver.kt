package tw.sleeptimer
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/** AlarmManager target: works even if the app process was killed. */
class PowerOffReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent?) {
    val pr = goAsync()
    val app = c.applicationContext
    val h = Handler(Looper.getMainLooper())
    var tries = 0
    fun go() {
      try {
        // After process death the a11y service may need a moment to rebind.
        if (PowerOffService.instance == null && Timer.isA11yEnabled(app) && tries < 8) {
          tries++; h.postDelayed({ go() }, 500); return
        }
        Timer.fire(app) { try { pr.finish() } catch (_: Exception) {} }
      } catch (e: Exception) { Log.e("SleepTimer", "fire", e); try { pr.finish() } catch (_: Exception) {} }
    }
    go()
  }
}

/** On boot elapsedRealtime resets and alarms are gone: drop any stale endAt. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(c: Context, i: Intent?) {
    if (i?.action == Intent.ACTION_BOOT_COMPLETED) Timer.clearState(c)
  }
}
