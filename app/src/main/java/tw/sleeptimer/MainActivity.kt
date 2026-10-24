package tw.sleeptimer
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
  override fun onCreate(b: Bundle?) {
    super.onCreate(b)
    // Voice/adb shortcut: am start -n tw.sleeptimer/.MainActivity --ei minutes 10
    intent.getIntExtra("minutes", 0).takeIf { it > 0 }?.let { start(it); finish(); return }
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48,48,48,48) }
    col.addView(TextView(this).apply { text = "定時關機"; textSize = 36f; gravity = Gravity.CENTER })
    val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
    listOf(5, 10, 15, 20).forEach { m -> row.addView(Button(this).apply { text = "$m 分鐘"; textSize = 26f; setOnClickListener { start(m) } }) }
    row.addView(Button(this).apply { text = "取消"; textSize = 26f; setOnClickListener {
      startService(Intent(this@MainActivity, TimerService::class.java).setAction(TimerService.CANCEL)); toast("已取消") } })
    col.addView(row)
    col.addView(Button(this).apply { text = "設定：開啟無障礙服務（必要，一次）"; setOnClickListener {
      startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
    col.addView(Button(this).apply { text = "備援：啟用裝置管理員"; setOnClickListener {
      startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
        ComponentName(this@MainActivity, AdminReceiver::class.java))) } })
    setContentView(col)
    row.getChildAt(1).requestFocus()
  }
  private fun start(m: Int) {
    startForegroundService(Intent(this, TimerService::class.java).putExtra("minutes", m))
    toast("$m 分鐘後關機")
  }
  private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
