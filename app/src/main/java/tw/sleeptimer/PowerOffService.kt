package tw.sleeptimer
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class PowerOffService : AccessibilityService() {
  companion object { @Volatile var instance: PowerOffService? = null }
  override fun onServiceConnected() { instance = this }
  override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
  override fun onDestroy() { instance = null; super.onDestroy() }
  override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
  override fun onInterrupt() {}
  // GLOBAL_ACTION_LOCK_SCREEN (API 28+) == power-button short press -> TV goes to standby on Google TV
  fun sleep() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
}
