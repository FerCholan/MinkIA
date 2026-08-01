package com.moviles.minkia.ui.splash

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.transicionFundido
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.data.model.Usuario
import com.moviles.minkia.databinding.ActivitySplashBinding
import com.moviles.minkia.ui.MainActivity
import com.moviles.minkia.ui.auth.AuthActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    private val prefs by lazy { PreferenciasRepository.create(this) }

    override fun inflateBinding(inflater: LayoutInflater) = ActivitySplashBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        barraEstadoOscura() // fondo claro (edge-to-edge ya viene de BaseActivity)

        trazarLogo()
        animarEntrada()
        animarPuntos()

        // Espera el splash y decide el destino según la sesión guardada: si el
        // usuario ya estaba logueado, salta onboarding y login y entra directo.
        lifecycleScope.launch {
            delay(SPLASH_MS)
            animadoresPuntos.forEach { it.cancel() }
            startActivity(Intent(this@SplashActivity, destinoSegunSesion(prefs.sesion.first())))
            transicionFundido() // el fondo andino se mantiene; solo cambia el contenido
            finish()
        }
    }

    /**
     * Con sesión: directo a la app. Sin sesión: al contenedor del flujo de
     * acceso (antes: OnboardingActivity). AuthActivity arranca en el onboarding
     * por defecto (sin el extra EXTRA_OMITIR_ONBOARDING), que es exactamente lo
     * que hacía esta rama antes del refactor.
     *
     * MainActivity es la única Activity contenedora tanto para ciudadano como
     * para administrador (FASE 2): ya no hay una Activity por rol. El rol de
     * `usuario` deja de decidirse acá; es MainActivity quien, al arrancar, lee
     * de nuevo la sesión y elige entre nav_ciudadano y nav_admin (ver
     * MainActivity.configurarGrafoSegunRol).
     */
    private fun destinoSegunSesion(usuario: Usuario?): Class<*> =
        if (usuario == null) AuthActivity::class.java else MainActivity::class.java

    /**
     * Dibuja el logotipo trazo a trazo (drawable/avd_logo_minkia.xml): cada letra se
     * traza con una linea fina y recien despues aparece rellena.
     */
    private fun trazarLogo() {
        (binding.ivLogo.drawable as? Animatable)?.start()
    }

    /** El eslogan entra cuando las letras ya estan casi dibujadas, para que se lea como una secuencia. */
    private fun animarEntrada() {
        binding.tvEslogan.alpha = 0f
        binding.tvEslogan.animate()
            .alpha(1f)
            .setStartDelay(ESLOGAN_DELAY_MS)
            .setDuration(ENTRADA_MS)
            .start()
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
        // El trazado del logo dura ~2000 ms (lo calcula design/scripts/gen-logo-avd.mjs,
        // constante RITMO); el resto es el respiro para verlo entero antes de navegar.
        private const val SPLASH_MS = 2450L
        private const val ESLOGAN_DELAY_MS = 1550L
        private const val ENTRADA_MS = 380L
        private const val PULSO_MS = 420L
        private const val DESFASE_MS = 160L
        private const val ALFA_MIN = 0.3f
    }
}
