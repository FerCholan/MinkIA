package com.moviles.minkia.ui.detalle

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.ActivityDetalleBinding
import com.moviles.minkia.ui.MainActivity

/**
 * Detalle y seguimiento de un reporte (mockup C15). Muestra la detección, los
 * chips (severidad, área, vecinos) y un timeline cuyo avance depende del estado.
 */
class DetalleActivity : BaseActivity<ActivityDetalleBinding>() {

    override fun inflateBinding(inflater: LayoutInflater) = ActivityDetalleBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        configurarTransicionCompartida()
        aplicarInsetSuperior(binding.toolbar)
        barrasCabeceraVerde() // toolbar verde arriba, fondo claro abajo
        // finishAfterTransition reproduce la animación de vuelta (contracción).
        binding.toolbar.setNavigationOnClickListener { supportFinishAfterTransition() }

        val ticket = intent.getStringExtra(EXTRA_TICKET).orEmpty()
        val severidad = runCatching { Severidad.valueOf(intent.getStringExtra(EXTRA_SEVERIDAD) ?: "") }
            .getOrDefault(Severidad.MEDIA)
        val estado = runCatching { EstadoReporte.valueOf(intent.getStringExtra(EXTRA_ESTADO) ?: "") }
            .getOrDefault(EstadoReporte.RECIBIDO)
        val area = intent.getDoubleExtra(EXTRA_AREA, 0.0)
        val vecinos = intent.getIntExtra(EXTRA_VECINOS, 0)

        binding.toolbar.title = getString(R.string.detalle_titulo, ticket)
        binding.chipIa.text = getString(R.string.detalle_ia, "Basura", 86)
        binding.chipArea.text = getString(R.string.detalle_area, area.toString())
        binding.chipVecinos.text = getString(R.string.detalle_vecinos, vecinos)

        binding.chipSeveridad.text = severidad.name
        binding.chipSeveridad.setTextColor(ContextCompat.getColor(this, colorSeveridad(severidad)))

        if (vecinos > 0) {
            binding.tvUnion.text = getString(R.string.detalle_union, vecinos)
        } else {
            binding.tvUnion.visibility = View.GONE
        }

        pintarTimeline(estado)
        binding.btnVerMapa.setOnClickListener { irAlMapa() }
    }

    /**
     * Si venimos de la lista con un elemento compartido, monta el container
     * transform: el contenido se expande desde la tarjeta tocada (y se contrae al
     * volver). Si no hay nombre de transición, no hace nada (entrada normal).
     */
    private fun configurarTransicionCompartida() {
        val nombre = intent.getStringExtra(EXTRA_TRANSITION) ?: return
        ViewCompat.setTransitionName(findViewById(android.R.id.content), nombre)
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        window.sharedElementEnterTransition = MaterialContainerTransform().apply {
            addTarget(android.R.id.content)
            duration = 340
            scrimColor = Color.TRANSPARENT
        }
        window.sharedElementReturnTransition = MaterialContainerTransform().apply {
            addTarget(android.R.id.content)
            duration = 300
            scrimColor = Color.TRANSPARENT
        }
    }

    private fun pintarTimeline(estado: EstadoReporte) {
        val activo = when (estado) {
            EstadoReporte.RECIBIDO, EstadoReporte.DUPLICADO -> 1
            EstadoReporte.EN_PROCESO -> 2
            EstadoReporte.EN_RUTA -> 3
            EstadoReporte.RESUELTO -> 4
        }
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        dots.forEachIndexed { idx, dot ->
            val paso = idx + 1
            pintarDot(
                dot,
                when {
                    paso < activo -> R.drawable.ic_check_chip
                    paso == activo && estado == EstadoReporte.RESUELTO -> R.drawable.ic_check_chip
                    paso == activo -> R.drawable.dot_activo
                    else -> R.drawable.dot_inactivo
                }
            )
        }
    }

    private fun pintarDot(dot: ImageView, recurso: Int) = dot.setImageResource(recurso)

    private fun colorSeveridad(s: Severidad) = when (s) {
        Severidad.ALTA -> R.color.sev_alta
        Severidad.MEDIA -> R.color.sev_media
        Severidad.BAJA -> R.color.sev_baja
    }

    private fun irAlMapa() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_MAPA)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    companion object {
        const val EXTRA_TICKET = "extra_ticket"
        const val EXTRA_DIRECCION = "extra_direccion"
        const val EXTRA_SEVERIDAD = "extra_severidad"
        const val EXTRA_AREA = "extra_area"
        const val EXTRA_VECINOS = "extra_vecinos"
        const val EXTRA_ESTADO = "extra_estado"
        const val EXTRA_TRANSITION = "extra_transition"
    }
}
