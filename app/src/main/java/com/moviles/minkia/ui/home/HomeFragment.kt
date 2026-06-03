package com.moviles.minkia.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.fragment.app.viewModels
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.animarEntrada
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.aplicarPulsacion
import com.moviles.minkia.core.recortarEsquinasInferiores
import com.moviles.minkia.data.model.ResumenCiudadano
import com.moviles.minkia.databinding.FragmentHomeBinding

/**
 * Pantalla de inicio del ciudadano (mockup C07). Patrón de referencia para las
 * demás pantallas: extiende [BaseFragment], observa un UiState y solo describe
 * cómo pintar cada estado. Sin lógica de negocio ni acceso a datos.
 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory() }

    private val adapter = PuntoCriticoAdapter()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentHomeBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerContenido.aplicarInsetSuperior()
        binding.headerInicio.recortarEsquinasInferiores()
        binding.rvPuntosCercanos.adapter = adapter
        binding.btnReintentar.setOnClickListener { viewModel.cargarResumen() }
        binding.btnNotificaciones.setOnClickListener {
            startActivity(
                android.content.Intent(
                    requireContext(),
                    com.moviles.minkia.ui.notificaciones.NotificacionesActivity::class.java
                )
            )
        }
        TooltipCompat.setTooltipText(binding.btnNotificaciones, getString(R.string.tooltip_notificaciones))
        binding.btnNotificaciones.aplicarPulsacion()

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> mostrarCarga()
                is UiState.Success -> mostrarDatos(state.data)
                is UiState.Error -> mostrarError(state.mensaje)
            }
        }
    }

    private fun mostrarCarga() {
        binding.skeleton.visibility = View.VISIBLE
        binding.skeleton.startShimmer()
        binding.contenido.visibility = View.GONE
        binding.grupoError.visibility = View.GONE
    }

    private fun mostrarDatos(resumen: ResumenCiudadano) {
        binding.skeleton.stopShimmer()
        binding.skeleton.visibility = View.GONE
        binding.grupoError.visibility = View.GONE
        binding.contenido.visibility = View.VISIBLE

        binding.tvPuntosActivos.text = resumen.puntosActivos.toString()
        binding.tvTusReportes.text = resumen.tusReportes.toString()
        binding.tvResueltos.text = formatearMiles(resumen.resueltos)
        binding.tvFocosCerca.text = "${resumen.focosCerca} focos cerca de ti"

        adapter.submitList(resumen.puntosCercanos) { binding.rvPuntosCercanos.scheduleLayoutAnimation() }
        binding.contenido.animarEntrada()
    }

    private fun mostrarError(mensaje: String) {
        binding.skeleton.stopShimmer()
        binding.skeleton.visibility = View.GONE
        binding.contenido.visibility = View.GONE
        binding.grupoError.visibility = View.VISIBLE
        binding.tvError.text = mensaje
    }

    private fun formatearMiles(valor: Int): String =
        if (valor >= 1000) "%.1fk".format(valor / 1000.0) else valor.toString()

    override fun onBindingDestroy() {
        binding.rvPuntosCercanos.adapter = null
    }
}
