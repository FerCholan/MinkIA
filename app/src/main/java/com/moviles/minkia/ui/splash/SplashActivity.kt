package com.moviles.minkia.ui.splash

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.transicionFundido
import com.moviles.minkia.databinding.ActivitySplashBinding
import com.moviles.minkia.ui.onboarding.OnboardingActivity

/**
 * Pantalla de bienvenida (mockup C01). Muestra el logotipo y el eslogan unos
 * segundos y luego deriva a la pantalla principal. Es el punto de entrada de la
 * app, por eso es una Activity y no un fragment.
 */
class SplashActivity : BaseActivity<ActivitySplashBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash de arranque del sistema, branded (debe ir antes de super.onCreate).
        installSplashScreen()
        super.onCreate(savedInstanceState)
    }

    // Pulsos de los puntos: se guardan para poder cancelarlos antes de navegar.
    private val animadoresPuntos = mutableListOf<Animator>()

    override fun inflateBinding(inflater: LayoutInflater) = ActivitySplashBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        barraEstadoOscura() // fondo claro (edge-to-edge ya viene de BaseActivity)

        animarEntrada()
        animarPuntos()

        Handler(Looper.getMainLooper()).postDelayed({
            animadoresPuntos.forEach { it.cancel() }
            startActivity(Intent(this, OnboardingActivity::class.java))
            transicionFundido() // el fondo andino se mantiene; solo cambia el contenido
            finish()
        }, SPLASH_MS)
    }

    /** Fade-in limpio del logo y el eslogan, sin desplazamientos: discreto. */
    private fun animarEntrada() {
        listOf(binding.ivLogo, binding.tvEslogan).forEach { vista ->
            vista.alpha = 0f
            vista.animate().alpha(1f).setDuration(ENTRADA_MS).start()
        }
    }

    /**
     * Convierte los 3 puntos en un indicador de carga: cada uno late en alfa,
     * desfasado del anterior, generando una onda que se repite mientras esperamos.
     */
    private fun animarPuntos() {
        listOf(binding.dot1, binding.dot2, binding.dot3).forEachIndexed { indice, punto ->
            punto.alpha = ALFA_MIN
            ObjectAnimator.ofFloat(punto, View.ALPHA, ALFA_MIN, 1f).apply {
                duration = PULSO_MS
                startDelay = indice * DESFASE_MS
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                animadoresPuntos.add(this)
                start()
            }
        }
    }

    companion object {
        private const val SPLASH_MS = 1300L
        private const val ENTRADA_MS = 280L
        private const val PULSO_MS = 420L
        private const val DESFASE_MS = 160L
        private const val ALFA_MIN = 0.3f
    }
}
