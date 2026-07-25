package com.moviles.minkia.ui.notificaciones

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.data.model.Notificacion
import com.moviles.minkia.data.model.TipoNotificacion
import com.moviles.minkia.databinding.FragmentNotificacionesBinding
import com.moviles.minkia.databinding.ItemNotificacionBinding

/**
 * Notificaciones del ciudadano (mockup C16), destino de nav_ciudadano. Agrupa
 * los avisos por sección (HOY / ESTA SEMANA) y los pinta con el ícono y color
 * según su tipo. Se abre desde el botón de campana de Inicio (HomeFragment).
 *
 * Pantalla sin argumentos: NotificacionesActivity ya se abría sin extras (la
 * lista sale entera de [NotificacionViewModel], que no cambia); es la conversión
 * más simple de la FASE 3, sin datos que "matar".
 */
class NotificacionesFragment : BaseFragment<FragmentNotificacionesBinding>() {

    private val viewModel: NotificacionViewModel by viewModels { NotificacionViewModel.Factory() }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNotificacionesBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.toolbar.aplicarInsetSuperior()
        // Cabecera verde: ya la fija MainActivity una sola vez para todo el host
        // (ver MainActivity.onViewReady); acá no hace falta repetirla.
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        viewModel.uiState.observe(viewLifecycleOwner) { estado ->
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
                    binding.scrollNotif.visibility = View.GONE
                    Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun pintar(notis: List<Notificacion>) {
        binding.contenedor.removeAllViews()
        if (notis.isEmpty()) {
            binding.contenedor.addView(vacio())
            return
        }
        // LayoutInflater.from(requireContext()) en vez de la propiedad
        // layoutInflater de la Activity original: es la forma explícita y segura
        // de obtener un inflater desde un fragment.
        val inflater = LayoutInflater.from(requireContext())
        notis.groupBy { it.grupo }.forEach { (grupo, items) ->
            binding.contenedor.addView(cabecera(grupo))
            items.forEach { binding.contenedor.addView(fila(inflater, it)) }
        }
    }

    /** Estado vacío: el vecino todavía no recibió ninguna notificación. */
    private fun vacio(): TextView = TextView(requireContext()).apply {
        text = getString(R.string.notif_vacio)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.texto_secundario))
        textSize = 14f
        gravity = Gravity.CENTER
        updatePadding(left = dp(20), top = dp(48), right = dp(20), bottom = dp(48))
    }

    private fun cabecera(texto: String): TextView = TextView(requireContext()).apply {
        text = texto
        setTextColor(ContextCompat.getColor(requireContext(), R.color.texto_secundario))
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        updatePadding(left = dp(20), top = dp(18), right = dp(20), bottom = dp(2))
    }

    private fun fila(inflater: LayoutInflater, n: Notificacion): View {
        val item = ItemNotificacionBinding.inflate(inflater, binding.contenedor, false)
        item.tvTexto.text = n.texto
        item.tvTiempo.text = n.tiempoTexto

        val (bg, icono, tinte) = estilo(n.tipo)
        item.ivIcono.setBackgroundResource(bg)
        item.ivIcono.setImageResource(icono)
        item.ivIcono.setColorFilter(ContextCompat.getColor(requireContext(), tinte))
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
