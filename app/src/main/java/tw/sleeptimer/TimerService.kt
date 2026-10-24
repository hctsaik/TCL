package tw.sleeptimer
import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log

/** Foreground service for display only; the actual power-off is driven by AlarmManager (PowerOffReceiver). */
class TimerService : Service() {
  companion object { const val CANCEL = "cancel"; const val EXTRA_MINUTES = "minutes" }
  private val h = Handler(Looper.getMainLooper())
  private val tick = object : Runnable { override fun run() { update() } }
  override fun onBind(i: Intent?): IBinder? = null

  private fun notif(text: String): Notification {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("t", getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    return Notification.Builder(this, "t").setContentTitle(getString(R.string.notif_title)).setContentText(text)
      .setSmallIcon(android.R.drawable.ic_lock_power_off).setOngoing(true).setContentIntent(open).build()
  }
  private fun text(ms: Long) = getString(R.string.notif_remaining, ((ms + 59_999) / 60_000).toInt())

  override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
    // Must call startForeground promptly after startForegroundService.
    startForeground(1, notif(getString(R.string.notif_title)))
    h.removeCallbacks(tick)
    if (i?.action == CANCEL) { Timer.cancel(this); stopSelfNow(); return START_NOT_STICKY }
    val min = i?.getIntExtra(EXTRA_MINUTES, 0) ?: 0
    if (min > 0) {
      if (!Timer.isReady(this)) { Log.w("SleepTimer", "not ready; refusing"); stopSelfNow(); return START_NOT_STICKY }
      Timer.schedule(this, min)
    }
    // i == null -> restarted by system (START_STICKY): recompute from persisted endAt.
    if (!Timer.isRunning(this)) { stopSelfNow(); return START_NOT_STICKY }
    update()
    return START_STICKY
  }

  private fun update() {
    try {
      val ms = Timer.remainingMs(this)
      if (ms < 0 && !Timer.isRunning(this)) { stopSelfNow(); return }
      if (ms <= 0) { Timer.fire(this); stopSelfNow(); return }
      getSystemService(NotificationManager::class.java).notify(1, notif(text(ms)))
      val next = ms % 60_000L
      h.postDelayed(tick, if (next == 0L) 60_000L else next)
    } catch (e: Exception) { Log.e("SleepTimer", "update", e); stopSelfNow() }
  }
  private fun stopSelfNow() { h.removeCallbacks(tick); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
  override fun onDestroy() { h.removeCallbacks(tick); super.onDestroy() }
}
