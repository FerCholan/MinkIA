package com.moviles.minkia

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * Application de MinkIA. Fija el modo claro de forma consistente: la identidad de
 * marca es cálida y clara (crema + patrón andino), y un dark mode propio sería un
 * rediseño aparte del sistema visual. Esto evita el dark "a medias" del sistema.
 */
class MinkIaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
