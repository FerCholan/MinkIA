package com.moviles.minkia.ui.admin

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.animarEntrada
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.core.recortarEsquinasInferiores
import com.moviles.minkia.data.model.KpiAdmin
import com.moviles.minkia.data.model.PanelAdmin
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.ZonaAfectada
import com.moviles.minkia.databinding.FragmentAdminPanelBinding

/**
 * Panel del administrador (mockup A01). KPIs y zonas más afectadas vienen del
 * repositorio vía [PanelViewModel]; el gráfico semanal queda como referencia
 * visual. La vista solo observa y pinta.
 */
class PanelFragment : BaseFragment<FragmentAdminPanelBinding>() {

    private val viewModel: PanelViewModel by viewModels { PanelViewModel.Factory() }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminPanelBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerPanelContenido.aplicarInsetSuperior()
        binding.headerPanel.recortarEsquinasInferiores()
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.skeletonPanel.mostrarSkeleton(true)
                    binding.scrollPanel.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.skeletonPanel.mostrarSkeleton(false)
                    binding.scrollPanel.visibility = View.VISIBLE
                    mostrarPanel(state.data)
                }
                is UiState.Error -> {
                    binding.skeletonPanel.mostrarSkeleton(false)
                    binding.scrollPanel.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun mostrarPanel(panel: PanelAdmin) {
        val kpis = panel.kpis
        pintarKpi(binding.tvKpi1Valor, binding.tvKpi1Delta, kpis.getOrNull(0))
        pintarKpi(binding.tvKpi2Valor, binding.tvKpi2Delta, kpis.getOrNull(1))
        pintarKpi(binding.tvKpi3Valor, binding.tvKpi3Delta, kpis.getOrNull(2))
        pintarKpi(binding.tvKpi4Valor, binding.tvKpi4Delta, kpis.getOrNull(3))

        // El alto de cada barra de zona es relativo a la zona con más focos.
        val maxFocos = panel.zonas.maxOfOrNull { it.focos }?.coerceAtLeast(1) ?: 1
        pintarZona(binding.tvZona1, binding.barraZona1, panel.zonas.getOrNull(0), maxFocos)
        pintarZona(binding.tvZona2, binding.barraZona2, panel.zonas.getOrNull(1), maxFocos)
        pintarZona(binding.tvZona3, binding.barraZona3, panel.zonas.getOrNull(2), maxFocos)

        binding.contenidoPanel.animarEntrada()
    }

    private fun pintarKpi(valor: TextView, delta: TextView, kpi: KpiAdmin?) {
        kpi ?: return
        valor.text = kpi.valor
        delta.text = kpi.delta
    }

    private fun pintarZona(nombre: TextView, barra: ProgressBar, zona: ZonaAfectada?, maxFocos: Int) {
        zona ?: return
        nombre.text = zona.nombre
        barra.progress = zona.focos * 100 / maxFocos
        barra.progressTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), colorSeveridad(zona.severidad))
        )
    }

    private fun colorSeveridad(severidad: Severidad): Int = when (severidad) {
        Severidad.ALTA -> R.color.sev_alta
        Severidad.MEDIA -> R.color.sev_media
        Severidad.BAJA -> R.color.sev_baja
    }
}
