package com.example.core.network

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor

data class ClockSkewStatus(
  val isCalibrated: Boolean = false,
  val offsetMs: Long = 0L,
) {
  val absoluteSkewSeconds: Long
    get() = abs(offsetMs) / 1000L

  val hasSignificantSkew: Boolean
    get() = isCalibrated && abs(offsetMs) >= ChainTimeOracle.SKEW_WARNING_THRESHOLD_MS
}

/**
 * Estimates device clock offset against HTTP `Date` headers returned by public TON endpoints
 * so `valid_until` and expiry checks stay aligned with TON validator consensus time even when
 * the phone's local clock is skewed.
 */
class ChainTimeOracle(
  private val deviceClockMs: () -> Long = { System.currentTimeMillis() },
) {
  private val calibratedOffsetMs = AtomicLong(0L)
  private val _status = MutableStateFlow(ClockSkewStatus())
  val status: StateFlow<ClockSkewStatus> = _status.asStateFlow()

  fun currentNetworkEpochMs(): Long = deviceClockMs() + calibratedOffsetMs.get()

  fun currentNetworkEpochSec(): Long = currentNetworkEpochMs() / 1000L

  fun recordServerTimestamp(serverEpochMs: Long, roundTripMs: Long = 0L) {
    if (serverEpochMs < MIN_VALID_SERVER_EPOCH_MS) return
    val clampedRtt = roundTripMs.coerceAtLeast(0L)
    if (clampedRtt > MAX_CALIBRATION_RTT_MS) return

    val localMidpointMs = deviceClockMs() - (clampedRtt / 2L)
    val measuredOffsetMs = serverEpochMs - localMidpointMs
    val current = _status.value
    val effectiveOffsetMs =
      if (current.isCalibrated && abs(measuredOffsetMs - current.offsetMs) < JITTER_DEADBAND_MS) {
        current.offsetMs
      } else {
        measuredOffsetMs
      }

    calibratedOffsetMs.set(effectiveOffsetMs)
    if (!current.isCalibrated || effectiveOffsetMs != current.offsetMs) {
      _status.value = ClockSkewStatus(isCalibrated = true, offsetMs = effectiveOffsetMs)
    }
  }

  fun interceptor(): Interceptor = Interceptor { chain ->
    val startMs = deviceClockMs()
    val response = chain.proceed(chain.request())
    val endMs = deviceClockMs()
    val serverInstant = response.headers.getInstant("Date")
    if (serverInstant != null) {
      recordServerTimestamp(
        serverEpochMs = serverInstant.toEpochMilli(),
        roundTripMs = (endMs - startMs).coerceAtLeast(0L),
      )
    }
    response
  }

  companion object {
    const val SKEW_WARNING_THRESHOLD_MS = 30_000L
    private const val JITTER_DEADBAND_MS = 2_000L
    private const val MAX_CALIBRATION_RTT_MS = 10_000L
    // 2023-11-14 UTC sanity floor to ignore unset epoch/placeholder dates.
    private const val MIN_VALID_SERVER_EPOCH_MS = 1_700_000_000_000L
  }
}
