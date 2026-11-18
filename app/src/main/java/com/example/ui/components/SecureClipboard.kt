package com.example.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

object SecureClipboard {
  const val DEFAULT_AUTO_CLEAR_MS = 60_000L

  private val mainHandler = Handler(Looper.getMainLooper())
  private val lock = Any()
  private var pendingText: String? = null
  private var pendingClearRunnable: Runnable? = null

  fun copyText(
    context: Context,
    label: String,
    text: String,
    isSensitive: Boolean = true,
    autoClearAfterMs: Long = DEFAULT_AUTO_CLEAR_MS,
  ) {
    val appContext = context.applicationContext ?: context
    val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return
    val clip =
      ClipData.newPlainText(label, text).apply {
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          description.extras =
            (description.extras ?: PersistableBundle()).apply {
              putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
      }
    clipboard.setPrimaryClip(clip)

    synchronized(lock) {
      pendingClearRunnable?.let { mainHandler.removeCallbacks(it) }
      pendingClearRunnable = null
      pendingText = null

      if (autoClearAfterMs > 0L) {
        pendingText = text
        val clearTask = Runnable {
          clearIfMatches(appContext, expectedText = text)
        }
        pendingClearRunnable = clearTask
        mainHandler.postDelayed(clearTask, autoClearAfterMs)
      }
    }
  }

  fun clearIfMatchesPending(context: Context) {
    val appContext = context.applicationContext ?: context
    val expected =
      synchronized(lock) {
        pendingClearRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingClearRunnable = null
        val text = pendingText
        pendingText = null
        text
      }
    if (expected != null) {
      clearIfMatches(appContext, expectedText = expected)
    }
  }

  private fun clearIfMatches(appContext: Context, expectedText: String) {
    synchronized(lock) {
      if (pendingText == expectedText) {
        pendingText = null
        pendingClearRunnable = null
      }
    }
    val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return
    val currentText =
      clipboard.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.text
        ?.toString()
    if (currentText == expectedText) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        clipboard.clearPrimaryClip()
      } else {
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
      }
    }
  }
}
