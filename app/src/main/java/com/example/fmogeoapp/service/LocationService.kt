package com.example.fmogeoapp.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 定位服务 - 封装 LocationManager
 */
class LocationService(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * 获取当前一次性位置（WGS84）
     * @param timeout 超时时间（毫秒），默认 10 秒
     * @return Location 对象，失败返回 null
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeout: Long = 10000L, preciseMode: Boolean = false): Location? {
        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var hasReceivedLocation = false
            var timeoutRunnable: Runnable? = null
            var locationListener: LocationListener? = null

            locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!hasReceivedLocation && continuation.isActive) {
                        hasReceivedLocation = true
                        timeoutRunnable?.let { handler.removeCallbacks(it) }
                        try {
                            locationManager.removeUpdates(this)
                        } catch (e: Exception) {
                        }
                        continuation.resume(location)
                    }
                }

                override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            timeoutRunnable = Runnable {
                if (continuation.isActive && !hasReceivedLocation) {
                    locationListener?.let {
                        try {
                            locationManager.removeUpdates(it)
                        } catch (e: Exception) {
                        }
                    }
                    continuation.resume(null)
                }
            }

            handler.postDelayed(timeoutRunnable!!, timeout)

            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

            if (preciseMode) {
                if (gpsEnabled) {
                    try {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            100L,
                            0f,
                            locationListener!!,
                            Looper.getMainLooper()
                        )
                    } catch (e: Exception) {
                    }
                } else {
                    timeoutRunnable?.let { handler.removeCallbacks(it) }
                    continuation.resume(null)
                }
            } else {
                if (networkEnabled) {
                    try {
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            100L,
                            0f,
                            locationListener!!,
                            Looper.getMainLooper()
                        )
                    } catch (e: Exception) {
                    }
                }

                if (gpsEnabled) {
                    try {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            100L,
                            0f,
                            locationListener!!,
                            Looper.getMainLooper()
                        )
                    } catch (e: Exception) {
                    }
                }

                if (!networkEnabled && !gpsEnabled) {
                    timeoutRunnable?.let { handler.removeCallbacks(it) }
                    continuation.resume(null)
                }
            }

            continuation.invokeOnCancellation {
                timeoutRunnable?.let { handler.removeCallbacks(it) }
                locationListener?.let {
                    try {
                        locationManager.removeUpdates(it)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    /**
     * 检查位置提供者是否可用
     */
    @Suppress("DEPRECATION")
    fun isLocationEnabled(): Boolean {
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return gpsEnabled || networkEnabled
    }
}
