package tw.sleeptimer
import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
  private lateinit var status: TextView
  private lateinit var countdown: TextView
  private lateinit var adbHelp: TextView
  private lateinit var setupBtn: Button
  private var focusBtn: View? = null
  private fun minBtn(m: Int) = Button(this).apply {
    text = getString(R.string.minutes_btn, m); textSize = 20f; setOnClickListener { start(m) } }
  private val h = Handler(Looper.getMainLooper())
  private val tick = object : Runnable { override fun run() { refresh(); h.postDelayed(this, 1000) } }

  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    // Voice/adb shortcut: am start -n tw.sleeptimer/.MainActivity --ei minutes 10
    if (b == null && handleShortcut(intent)) { finish(); return }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 32, 48, 32) }
    col.addView(TextView(this).apply { setText(R.string.app_name); textSize = 36f; gravity = Gravity.CENTER })
    status = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER }
    countdown = TextView(this).apply { textSize = 24f; gravity = Gravity.CENTER }
    col.addView(status); col.addView(countdown)
    val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
    listOf(5, 10, 15).forEach { m -> row.addView(minBtn(m)) }
    col.addView(row)
    val rowB = LinearLayout(this).apply { gravity = Gravity.CENTER }
    listOf(20, 30, 60).forEach { m -> rowB.addView(minBtn(m)) }
    rowB.addView(Button(this).apply { setText(R.string.cancel); textSize = 20f; setOnClickListener {
      if (Timer.cancel(this@MainActivity)) toast(getString(R.string.cancelled)); refresh() } })
    col.addView(rowB)
    val row2 = LinearLayout(this).apply { gravity = Gravity.CENTER }
    row2.addView(Button(this).apply { setText(R.string.test_now); setOnClickListener { testNow() } })
    setupBtn = Button(this).apply { setText(R.string.setup_a11y); setOnClickListener {
      openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
    row2.addView(setupBtn)
    row2.addView(Button(this).apply { setText(R.string.setup_admin); setOnClickListener {
      openSettings(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, AdminReceiver::class.java))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explain))) } })
    col.addView(row2)
    adbHelp = TextView(this).apply { setText(R.string.adb_help); textSize = 14f; visibility = View.GONE }
    col.addView(adbHelp)
    setContentView(ScrollView(this).apply { isFillViewport = true; addView(col) })
    focusBtn = row.getChildAt(1); focusBtn?.requestFocus()
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
      requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    if (intent.getBooleanExtra(Timer.EXTRA_ERROR, false)) showError()
  }

  override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
    super.onRequestPermissionsResult(code, p, r); focusBtn?.requestFocus()
  }

  override fun onNewIntent(i: Intent) {
    super.onNewIntent(i); setIntent(i)
    if (i.getBooleanExtra(Timer.EXTRA_ERROR, false)) showError() else handleShortcut(i)
    refresh()
  }

  /** Returns true if a valid minutes extra was handled. Invalid (<=0) values are ignored. */
  private fun handleShortcut(i: Intent): Boolean {
    val m = i.getIntExtra(TimerService.EXTRA_MINUTES, 0)
    if (m <= 0) return false
    start(m)
    return Timer.isRunning(this)
  }

  private fun showError() {
    if (isDestroyed || isFinishing || !::adbHelp.isInitialized) return
    toast(getString(R.string.poweroff_failed)); adbHelp.visibility = View.VISIBLE; setupBtn.requestFocus() }

  override fun onResume() {
    super.onResume()
    if (Timer.isRunning(this) && !Timer.isAlarmPending(this)) Timer.clearState(this) // stale (force-stop/reboot)
    refresh(); h.removeCallbacks(tick); h.post(tick) }
  override fun onPause() { h.removeCallbacks(tick); super.onPause() }

  private fun refresh() {
    if (!::status.isInitialized) return
    val a = getString(when {
      !Timer.isA11yEnabled(this) -> R.string.a11y_off
      Timer.isA11yConnected() -> R.string.a11y_on
      else -> R.string.a11y_unbound })
    val d = getString(if (Timer.isAdminActive(this)) R.string.admin_on else R.string.admin_off)
    status.text = "$a　　$d"
    refreshCountdown()
  }
  private fun refreshCountdown() {
    if (!::countdown.isInitialized) return
    val ms = Timer.remainingMs(this)
    countdown.text = if (!Timer.isRunning(this)) getString(R.string.not_running) else {
      val s = (maxOf(ms, 0L) + 999) / 1000; getString(R.string.remaining, (s / 60).toInt(), (s % 60).toInt()) }
  }

  private fun start(m: Int) {
    if (!Timer.isReady(this)) {
      toast(getString(R.string.not_ready))
      if (::setupBtn.isInitialized) { adbHelp.visibility = View.VISIBLE; setupBtn.requestFocus() }
      else startActivity(Intent(this, MainActivity::class.java))
      return
    }
    Timer.schedule(this, m)
    try { startForegroundService(Intent(this, TimerService::class.java)) } catch (_: Exception) { /* alarm still fires */ }
    toast(getString(R.string.started, m))
    refresh()
  }

  private fun testNow() {
    if (!Timer.isReady(this)) { toast(getString(R.string.not_ready)); adbHelp.visibility = View.VISIBLE; setupBtn.requestFocus(); return }
    toast(getString(R.string.test_in_3))
    h.postDelayed({ Timer.powerOff(applicationContext) { ok -> if (!ok) showError() } }, 3000)
  }

  private fun openSettings(i: Intent) {
    try { startActivity(i) } catch (e: ActivityNotFoundException) {
      adbHelp.visibility = View.VISIBLE
      toast(getString(R.string.settings_missing))
      try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: ActivityNotFoundException) {}
    }
  }
  private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
