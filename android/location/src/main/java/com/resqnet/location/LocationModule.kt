package com.resqnet.location

import android.content.Context

/**
 * Entry point for GPS / SOS location functionality.
 * Does not include a UI; the app module owns screens.
 */
object LocationModule {
    fun createHelper(context: Context): LocationHelper = LocationHelper(context)
}
