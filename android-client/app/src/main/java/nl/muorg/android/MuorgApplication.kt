package nl.muorg.android

import android.app.Application
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MuorgApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try { CastContext.getSharedInstance(this) } catch (_: Exception) {}
    }
}
