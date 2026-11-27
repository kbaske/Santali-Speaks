package app.santals.speaks

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt creates the dependency graph from here.
 * Kept minimal on purpose: heavy work is done lazily by the components that need it.
 */
@HiltAndroidApp
class SantaliSpeaksApp : Application()
