package com.moviles.minkia.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.core.view.updateLayoutParams
import androidx.viewpager2.widget.ViewPager2
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.transicionFundido
import com.moviles.minkia.databinding.ActivityOnboardingBinding
import com.moviles.minkia.ui.login.LoginActivity

/**
 * Onboarding de bienvenida (mockup C02): tres diapositivas deslizables con un
 * indicador de puntos. "Saltar" o terminar la última lleva a la app. La
 * navegación de salida apuntará a Login cuando esa pantalla exista.
 */
class OnboardingActivity : BaseActivity<ActivityOnboardingBinding>() {

    private val pages = listOf(
        OnboardingPage(R.drawable.ic_ob_camara, R.string.ob1_titulo, R.string.ob1_desc),
        OnboardingPage(R.drawable.ic_ob_mapa, R.string.ob2_titulo, R.string.ob2_desc),
        OnboardingPage(R.drawable.ic_ob_comunidad, R.string.ob3_titulo, R.string.ob3_desc)
    )

    override fun inflateBinding(inflater: LayoutInflater) = ActivityOnboardingBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetsVerticales(binding.onboardingRoot)
        barraEstadoOscura() // fondo claro (edge-to-edge ya viene de BaseActivity)
        binding.viewPager.adapter = OnboardingAdapter(pages)
        binding.viewPager.setPageTransformer(OnboardingPageTransformer())
        construirIndicador(pages.size)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                actualizarIndicador(position)
                val ultima = position == pages.size - 1
                binding.btnSiguiente.setText(if (ultima) R.string.ob_empezar else R.string.ob_siguiente)
            }
        })

        binding.btnSaltar.setOnClickListener { terminar() }
        binding.btnSiguiente.setOnClickListener {
            val actual = binding.viewPager.currentItem
            if (actual < pages.size - 1) {
                binding.viewPager.currentItem = actual + 1
            } else {
                terminar()
            }
        }
    }

    private fun terminar() {
        startActivity(Intent(this, LoginActivity::class.java))
        transicionFundido()
        finish()
    }

    private fun construirIndicador(cantidad: Int) {
        val tamano = (8 * resources.displayMetrics.density).toInt()
        val margen = (3 * resources.displayMetrics.density).toInt()
        repeat(cantidad) {
            val punto = ImageView(this)
            punto.setImageResource(R.drawable.dot_inactivo)
            binding.dotsIndicator.addView(punto)
            punto.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                width = tamano; height = tamano
                marginStart = margen; marginEnd = margen
            }
        }
        actualizarIndicador(0)
    }

    private fun actualizarIndicador(activo: Int) {
        for (i in 0 until binding.dotsIndicator.childCount) {
            val punto = binding.dotsIndicator.getChildAt(i) as ImageView
            val esActivo = i == activo
            punto.setImageResource(if (esActivo) R.drawable.dot_activo else R.drawable.dot_inactivo)
            // El punto activo crece suave; los demás vuelven a su tamaño.
            val escala = if (esActivo) 1.4f else 1f
            punto.animate().scaleX(escala).scaleY(escala).setDuration(200).start()
        }
    }
}
