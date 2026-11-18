package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.gramvault.wallet.BuildConfig

@Composable
fun SecureScreenEffect() {
  if (isDebugEmulatorEnvironment()) return
  val context = LocalContext.current
  val view = LocalView.current
  DisposableEffect(context, view) {
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
    val activityWindow = context.findActivity()?.window
    val targetWindow = dialogWindow ?: activityWindow
    val alreadySecured =
      ((targetWindow?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
    if (!alreadySecured) {
      targetWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    onDispose {
      if (!alreadySecured) {
        targetWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    }
  }
}

internal fun isDebugEmulatorEnvironment(
  isDebug: Boolean = BuildConfig.DEBUG,
  fingerprint: String = Build.FINGERPRINT,
  hardware: String = Build.HARDWARE,
  product: String = Build.PRODUCT,
  model: String = Build.MODEL,
  manufacturer: String = Build.MANUFACTURER,
): Boolean {
  if (!isDebug) return false
  if (fingerprint.equals("robolectric", ignoreCase = true)) return false
  return fingerprint.startsWith("generic", ignoreCase = true) ||
    fingerprint.startsWith("unknown", ignoreCase = true) ||
    fingerprint.contains("emulator", ignoreCase = true) ||
    fingerprint.contains("sdk_gphone", ignoreCase = true) ||
    hardware.contains("goldfish", ignoreCase = true) ||
    hardware.contains("ranchu", ignoreCase = true) ||
    hardware.contains("vbox86", ignoreCase = true) ||
    product.contains("sdk", ignoreCase = true) ||
    product.contains("emulator", ignoreCase = true) ||
    product.contains("simulator", ignoreCase = true) ||
    model.contains("Emulator", ignoreCase = true) ||
    model.contains("Android SDK built for", ignoreCase = true) ||
    manufacturer.contains("Genymotion", ignoreCase = true)
}

private tailrec fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
