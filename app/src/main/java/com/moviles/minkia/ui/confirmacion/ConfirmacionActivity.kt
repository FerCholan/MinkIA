package com.moviles.minkia.ui.confirmacion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.animation.OvershootInterpolator
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.vibrar
import com.moviles.minkia.databinding.ActivityConfirmacionBinding
import com.moviles.minkia.ui.MainActivity

/**
 * Confirmación y sincronización del reporte (mockup C12). Muestra el ticket y el
 * estado del seguimiento. Cierra el flujo de reporte volviendo al inicio (o al
 * mapa), reiniciando la pila para que "atrás" no vuelva al formulario.
 */
class ConfirmacionActivity : BaseActivity<ActivityConfirmacionBinding>() {

    override fun inflateBinding(inflater: LayoutInflater) = ActivityConfirmacionBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetsVerticales(binding.confirmacionRoot)
        barraEstadoOscura() // fondo claro

        binding.tvTicket.text = intent.getStringExtra(EXTRA_TICKET).orEmpty()

        animarExito()

        binding.btnVolverInicio.setOnClickListener { irAInicio(MainActivity.TAB_INICIO) }
        binding.btnVerMapa.setOnClickListener { irAInicio(MainActivity.TAB_MAPA) }
    }

    /** Momento de logro: el check "explota" suave con un rebote y una vibración. */
    private fun animarExito() {
        binding.ivCheck.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0f
            animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setInterpolator(OvershootInterpolator())
                .setDuration(480)
                .withEndAction { vibrar() }
                .start()
        }
    }

    private fun irAInicio(tab: Int) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_TICKET = "extra_ticket"
    }
}
