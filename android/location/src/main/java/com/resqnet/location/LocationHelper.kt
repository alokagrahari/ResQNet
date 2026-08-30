package com.resqnet.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class LocationHelper(private val context: Context) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCurrentLocation(
        onLocationReceived: (latitude: Double, longitude: Double) -> Unit,
        onLocationError: () -> Unit
    ) {
        getCurrentFix(
            onFixReceived = { fix ->
                onLocationReceived(fix.latitude, fix.longitude)
            },
            onLocationError = onLocationError
        )
    }

    @SuppressLint("MissingPermission")
    fun getCurrentFix(
        onFixReceived: (LocationFix) -> Unit,
        onLocationError: () -> Unit
    ) {
        if (!hasLocationPermission()) {
            onLocationError()
            return
        }

        val cancellation = CancellationTokenSource()
        val timeout = Runnable { cancellation.cancel() }
        mainHandler.postDelayed(timeout, CURRENT_FIX_TIMEOUT_MS)

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellation.token
        ).addOnCompleteListener { task ->
            mainHandler.removeCallbacks(timeout)
            val location = if (task.isSuccessful) task.result else null
            if (location != null) {
                onFixReceived(toFix(location, isLastKnown = false))
            } else {
                readLastKnown(onFixReceived, onLocationError)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnown(
        onFixReceived: (LocationFix) -> Unit,
        onLocationError: () -> Unit
    ) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onFixReceived(toFix(location, isLastKnown = true))
                } else {
                    onLocationError()
                }
            }
            .addOnFailureListener {
                onLocationError()
            }
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocationGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted || coarseLocationGranted
    }

    private fun toFix(location: Location, isLastKnown: Boolean): LocationFix {
        return LocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = location.time,
            isLastKnown = isLastKnown
        )
    }

    companion object {
        private const val CURRENT_FIX_TIMEOUT_MS = 10_000L
    }
}
