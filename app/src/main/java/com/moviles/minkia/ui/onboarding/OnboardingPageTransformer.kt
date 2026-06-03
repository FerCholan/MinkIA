package com.moviles.minkia.ui.onboarding

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.moviles.minkia.R
import kotlin.math.abs

/**
 * Transición sutil entre slides del onboarding: la página se atenúa y encoge un
 * poco al salir de cuadro, y el ícono se desplaza con un leve parallax (sensación
 * de profundidad). Le da vida al swipe sin estridencias, en línea con el tono
 * "mínimo y con intención" de la app.
 */
class OnboardingPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val acotada = abs(position).coerceAtMost(1f)

        // Atenúa y encoge la página a medida que se aleja del centro.
        page.alpha = 1f - acotada * 0.5f
        val escala = ESCALA_MIN + (1f - acotada) * (1f - ESCALA_MIN)
        page.scaleX = escala
        page.scaleY = escala

        // Parallax del ícono: se mueve más lento que la página, dando profundidad.
        page.findViewById<View?>(R.id.ivIcono)?.translationX = -position * page.width * PARALLAX
    }

    companion object {
        private const val ESCALA_MIN = 0.92f
        private const val PARALLAX = 0.12f
    }
}
