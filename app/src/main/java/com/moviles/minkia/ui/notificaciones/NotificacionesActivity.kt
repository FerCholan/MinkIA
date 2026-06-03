package com.moviles.minkia.ui.notificaciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.data.model.Notificacion
import com.moviles.minkia.data.model.TipoNotificacion
import com.moviles.minkia.databinding.ActivityNotificacionesBinding
import com.moviles.minkia.databinding.ItemNotificacionBinding

/**
 * Notificaciones del ciudadano (mockup C16). Agrupa los avisos por sección
 * (HOY / ESTA SEMANA) y los pinta con el ícono y color según su tipo.
 */
class NotificacionesActivity : BaseActivity<ActivityNotificacionesBinding>() {

    private val viewModel: NotificacionViewModel by viewModels { NotificacionViewModel.Factory() }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityNotificacionesBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetSuperior(binding.toolbar)
        barrasCabeceraVerde() // toolbar verde arriba, fondo claro abajo
        binding.toolbar.setNavigationOnClickListener { finish() }

        viewModel.uiState.observe(this) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    binding.skeletonNotif.root.mostrarSkeleton(true)
                    binding.scrollNotif.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.skeletonNotif.root.mostrarSkeleton(false)
                    binding.scrollNotif.visibility = View.VISIBLE
                    pintar(estado.data)
                }
                is UiState.Error -> {
                    binding.skeletonNotif.root.mostrarSkeleton(false)
                    binding.scrollNotif.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun pintar(notis: List<Notificacion>) {
        binding.contenedor.removeAllViews()
        val inflater = layoutInflater
        notis.groupBy { it.grupo }.forEach { (grupo, items) ->
            binding.contenedor.addView(cabecera(grupo))
            items.forEach { binding.contenedor.addView(fila(inflater, it)) }
        }
    }

    private fun cabecera(texto: String): TextView = TextView(this).apply {
        text = texto
        setTextColor(ContextCompat.getColor(this@NotificacionesActivity, R.color.texto_secundario))
        textSize = 12f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        updatePadding(left = dp(20), top = dp(18), right = dp(20), bottom = dp(2))
    }

    private fun fila(inflater: LayoutInflater, n: Notificacion): android.view.View {
        val item = ItemNotificacionBinding.inflate(inflater, binding.contenedor, false)
        item.tvTexto.text = n.texto
        item.tvTiempo.text = n.tiempoTexto

        val (bg, icono, tinte) = estilo(n.tipo)
        item.ivIcono.setBackgroundResource(bg)
        item.ivIcono.setImageResource(icono)
        item.ivIcono.setColorFilter(ContextCompat.getColor(this, tinte))
        return item.root
    }

    private fun estilo(tipo: TipoNotificacion): Triple<Int, Int, Int> = when (tipo) {
        TipoNotificacion.VALIDADO -> Triple(R.drawable.bg_icono_notif, R.drawable.ic_shield, R.color.verde_bosque)
        TipoNotificacion.INSIGNIA -> Triple(R.drawable.bg_icono_camara, R.drawable.ic_check, R.color.naranja_terracota)
        TipoNotificacion.RESUELTO -> Triple(R.drawable.bg_icono_notif, R.drawable.ic_check, R.color.verde_bosque)
        TipoNotificacion.UNIFICADO -> Triple(R.drawable.bg_icono_ubicacion, R.drawable.ic_shield, R.color.azul)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
