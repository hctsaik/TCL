package tw.sleeptimer
import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.*

class TimerService : Service() {
  companion object { const val CANCEL = "cancel" }
  private var timer: CountDownTimer? = null
  override fun onBind(i: Intent?) = null
  override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
    timer?.cancel()
    if (i?.action == CANCEL) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
    val min = i?.getIntExtra("minutes", 10) ?: 10
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("t", "定時關機", NotificationManager.IMPORTANCE_LOW))
    fun notif(s: String) = Notification.Builder(this, "t").setContentTitle("定時關機").setContentText(s)
      .setSmallIcon(android.R.drawable.ic_lock_power_off).setOngoing(true).build()
    startForeground(1, notif("$min 分鐘後關機"))
    timer = object : CountDownTimer(min * 60_000L, 1000) {
      override fun onTick(ms: Long) { if (ms / 1000 % 30 == 0L) nm.notify(1, notif("剩 ${ms / 60000} 分 ${ms / 1000 % 60} 秒")) }
      override fun onFinish() { powerOff(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }.start()
    return START_NOT_STICKY
  }
  private fun powerOff() {
    if (PowerOffService.instance?.sleep() == true) return
    val dpm = getSystemService(DevicePolicyManager::class.java)
    if (dpm.isAdminActive(ComponentName(this, AdminReceiver::class.java))) dpm.lockNow()
  }
  override fun onDestroy() { timer?.cancel(); super.onDestroy() }
}
