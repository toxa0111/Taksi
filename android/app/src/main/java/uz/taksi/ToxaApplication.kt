package uz.taksi

import android.app.Application
import com.yandex.mapkit.MapKitFactory

/**
 * MapKit API kalitini Activity yaratilishidan oldin, jarayon boshida bir marta o'rnatadi.
 * Activity qayta ochilganda setApiKey() qayta chaqirilishi MapKit'da crash keltirib chiqaradi.
 */
class ToxaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val apiKey = BuildConfig.MAPKIT_API_KEY
        if (apiKey.isNotBlank()) {
            MapKitFactory.setApiKey(apiKey)
        }
    }
}
