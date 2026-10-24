package tw.sleeptimer
import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/** Shared timer state + power-off logic. endAt is in SystemClock.elapsedRealtime() terms. */
object Timer {
  private const val TAG = "SleepTimer"
  private const val PREFS = "timer"
  private const val KEY_END = "endAt"
  const val ALERT_CHANNEL = "alert"
  const val EXTRA_ERROR = "error"

  fun isA11yEnabled(c: Context): Boolean {
    val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.split(':').any { it.startsWith(c.packageName + "/") }
  }
  fun isAdminActive(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
    .isAdminActive(ComponentName(c, AdminReceiver::class.java))
  fun isReady(c: Context) = isA11yEnabled(c) || isAdminActive(c)

  private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  /** 0 when no timer. */
  fun endAt(c: Context) = prefs(c).getLong(KEY_END, 0L)
  fun remainingMs(c: Context): Long { val e = endAt(c); return if (e == 0L) -1 else e - SystemClock.elapsedRealtime() }
  fun isRunning(c: Context) = endAt(c) != 0L

  private fun alarmIntent(c: Context) = PendingIntent.getBroadcast(c, 0, Intent(c, PowerOffReceiver::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

  fun schedule(c: Context, minutes: Int) {
    val end = SystemClock.elapsedRealtime() + minutes * 60_000L
    prefs(c).edit().putLong(KEY_END, end).commit()
    val am = c.getSystemService(AlarmManager::class.java)
    val pi = alarmIntent(c)
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    try {
      if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, pi)
      else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, pi)
    } catch (e: SecurityException) {
      am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, pi)
    }
  }

  /** Returns true if a timer was running. */
  fun cancel(c: Context): Boolean {
    val was = isRunning(c)
    prefs(c).edit().remove(KEY_END).commit()
    c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c))
    c.stopService(Intent(c, TimerService::class.java))
    return was
  }

  /** Try to put the TV into standby. Returns true on success. */
  fun powerOff(c: Context): Boolean {
    try {
      if (PowerOffService.instance?.sleep() == true) return true
    } catch (e: Exception) { Log.w(TAG, "a11y sleep failed", e) }
    try {
      if (isAdminActive(c)) { c.getSystemService(DevicePolicyManager::class.java).lockNow(); return true }
    } catch (e: Exception) { Log.w(TAG, "lockNow failed", e) }
    return false
  }

  /** Called when the countdown ends (alarm or service). */
  fun fire(c: Context) {
    prefs(c).edit().remove(KEY_END).commit()
    c.getSystemService(AlarmManager::class.java).cancel(alarmIntent(c))
    var ok = false
    try { ok = powerOff(c) } catch (e: Exception) { Log.e(TAG, "powerOff", e) }
    if (!ok) reportFailure(c)
    try { c.stopService(Intent(c, TimerService::class.java)) } catch (_: Exception) {}
  }

  fun reportFailure(c: Context) {
    val msg = c.getString(R.string.poweroff_failed)
    try {
      val nm = c.getSystemService(NotificationManager::class.java)
      nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, c.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH))
      val open = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java).putExtra(EXTRA_ERROR, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
      nm.notify(2, Notification.Builder(c, ALERT_CHANNEL).setContentTitle(c.getString(R.string.notif_title))
        .setContentText(msg).setStyle(Notification.BigTextStyle().bigText(msg))
        .setSmallIcon(android.R.drawable.stat_notify_error).setContentIntent(open).setAutoCancel(true).build())
    } catch (e: Exception) { Log.e(TAG, "notify", e) }
    try {
      c.startActivity(Intent(c, MainActivity::class.java).putExtra(EXTRA_ERROR, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { Log.w(TAG, "launch activity (may be blocked from background)", e) }
  }
}
