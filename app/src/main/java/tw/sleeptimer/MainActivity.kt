package tw.sleeptimer
import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.*

class MainActivity : Activity() {
  private lateinit var status: TextView
  private lateinit var countdown: TextView
  private lateinit var adbHelp: TextView
  private lateinit var setupBtn: Button
  private lateinit var selected: TextView
  private var focusBtn: View? = null
  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
  private fun rect(fill: Int, stroke: Int = 0, sc: Int = 0) = GradientDrawable().apply {
    cornerRadius = dp(12).toFloat(); setColor(fill); if (stroke > 0) setStroke(stroke, sc) }
  /** Strong TV focus style: amber fill + thick white border + black bold text, scale 1.15 when focused. */
  private fun styled(b: Button): Button = b.apply {
    val pressed = rect(0xFFFFA000.toInt(), dp(4), Color.WHITE)
    val focused = rect(0xFFFFC107.toInt(), dp(4), Color.WHITE)
    background = StateListDrawable().apply {
      addState(intArrayOf(android.R.attr.state_pressed), pressed)
      addState(intArrayOf(android.R.attr.state_focused), focused)
      addState(intArrayOf(), rect(0xFF333333.toInt())) }
    stateListAnimator = null; isAllCaps = false; isFocusable = true
    setPadding(dp(20), dp(10), dp(20), dp(10))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
      .apply { setMargins(dp(16), dp(12), dp(16), dp(12)) }
    applyFocus(this, false)
    setOnFocusChangeListener { v, f -> applyFocus(v as Button, f) }
  }
  private fun applyFocus(b: Button, f: Boolean) {
    b.setTextColor(if (f) Color.BLACK else 0xFFCCCCCC.toInt())
    b.setTypeface(null, if (f) Typeface.BOLD else Typeface.NORMAL)
    b.animate().scaleX(if (f) 1.15f else 1f).scaleY(if (f) 1.15f else 1f).translationZ(if (f) dp(12).toFloat() else 0f).setDuration(120).start()
    if (f && ::selected.isInitialized) selected.text = getString(R.string.current_selection, b.text)
  }
  private fun minBtn(m: Int) = styled(Button(this).apply {
    text = getString(R.string.minutes_btn, m); textSize = 20f; setOnClickListener { start(m) } })
  private val h = Handler(Looper.getMainLooper())
  private val tick = object : Runnable { override fun run() { refresh(); h.postDelayed(this, 1000) } }

  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    // Voice/adb shortcut: am start -n tw.sleeptimer/.MainActivity --ei minutes 10
    if (b == null && handleShortcut(intent)) { finish(); return }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 32, 48, 32); clipChildren = false; clipToPadding = false }
    window.decorView.setBackgroundColor(0xFF101010.toInt())
    col.addView(TextView(this).apply { setText(R.string.app_name); textSize = 36f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) })
    status = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER; setTextColor(0xFFDDDDDD.toInt()) }
    countdown = TextView(this).apply { textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
    selected = TextView(this).apply { textSize = 30f; gravity = Gravity.CENTER; setTextColor(0xFFFFC107.toInt()); setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, dp(8)) }
    col.addView(status); col.addView(countdown); col.addView(selected)
    val row = LinearLayout(this).apply { gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
    listOf(5, 10, 15).forEach { m -> row.addView(minBtn(m)) }
    col.addView(row)
    val rowB = LinearLayout(this).apply { gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
    listOf(20, 30, 60).forEach { m -> rowB.addView(minBtn(m)) }
    rowB.addView(styled(Button(this).apply { setText(R.string.cancel); textSize = 20f; setOnClickListener {
      if (Timer.cancel(this@MainActivity)) toast(getString(R.string.cancelled)); refresh() } }))
    col.addView(rowB)
    val row2 = LinearLayout(this).apply { gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false }
    row2.addView(styled(Button(this).apply { setText(R.string.test_now); textSize = 18f; setOnClickListener { testNow() } }))
    setupBtn = styled(Button(this).apply { setText(R.string.setup_a11y); textSize = 18f; setOnClickListener {
      openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
    row2.addView(setupBtn)
    row2.addView(styled(Button(this).apply { setText(R.string.setup_admin); textSize = 18f; setOnClickListener {
      openSettings(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, AdminReceiver::class.java))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explain))) } }))
    col.addView(row2)
    adbHelp = TextView(this).apply { setText(R.string.adb_help); textSize = 14f; setTextColor(0xFFDDDDDD.toInt()); visibility = View.GONE }
    col.addView(adbHelp)
    setContentView(ScrollView(this).apply { isFillViewport = true; clipChildren = false; setBackgroundColor(0xFF101010.toInt()); addView(col) })
    focusBtn = if (Timer.isReady(this)) row.getChildAt(1) else setupBtn; focusBtn?.requestFocus()
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
