package com.moviles.minkia.ui.reportes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.databinding.FragmentReportesBinding

/**
 * Historial de reportes del ciudadano (mockups C13 y C14). Lista los reportes,
 * filtra por pestaña (Todos/Pendientes/Resueltos) y muestra el estado vacío
 * cuando no hay. Tocar un reporte abre su detalle.
 */
class ReportesFragment : BaseFragment<FragmentReportesBinding>() {

    private val viewModel: ReportesViewModel by viewModels { ReportesViewModel.Factory() }
    private val adapter = MiReporteAdapter { vista, r -> abrirDetalle(vista, r) }

    private var todos: List<MiReporte> = emptyList()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentReportesBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerReportesContenido.aplicarInsetSuperior()
        binding.rvReportes.adapter = adapter
        binding.btnPrimerReporte.setOnClickListener { abrirCaptura() }

        binding.tabs.apply {
            addTab(newTab().setText(R.string.reportes_tab_todos))
            addTab(newTab().setText(R.string.reportes_tab_pendientes))
            addTab(newTab().setText(R.string.reportes_tab_resueltos))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = aplicarFiltro(tab.position)
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }

        viewModel.uiState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    binding.skeletonReportes.root.mostrarSkeleton(true)
                    binding.rvReportes.visibility = View.GONE
                    binding.grupoVacio.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.skeletonReportes.root.mostrarSkeleton(false)
                    todos = estado.data
                    aplicarFiltro(binding.tabs.selectedTabPosition.coerceAtLeast(0))
                }
                is UiState.Error -> {
                    binding.skeletonReportes.root.mostrarSkeleton(false)
                    binding.rvReportes.visibility = View.GONE
                    binding.grupoVacio.visibility = View.GONE
                    android.widget.Toast.makeText(
                        requireContext(), estado.mensaje, android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /** 0 = Todos, 1 = Pendientes, 2 = Resueltos. */
    private fun aplicarFiltro(posicion: Int) {
        val lista = when (posicion) {
            1 -> todos.filter { it.estado.esPendiente }
            2 -> todos.filter { it.estado.esResuelto }
            else -> todos
        }
        adapter.submitList(lista) { binding.rvReportes.scheduleLayoutAnimation() }
        binding.grupoVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        binding.rvReportes.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Abre el Detalle del reporte tocado (C15). Antes viajaban once extras
     * completos (todo MiReporte) más el nombre de la transición; ahora solo el
     * ticket, que DetalleFragment usa para pedirle el reporte entero al
     * repositorio Y para recalcular el mismo nombre de transición (no hace
     * falta mandarlo aparte).
     *
     * El reporte tocado sigue siendo el elemento compartido que se expande hacia
     * el detalle (container transform): FragmentNavigatorExtras es el
     * equivalente fragment-a-fragment de ActivityOptionsCompat.makeSceneTransitionAnimation,
     * que es lo que usaba esta misma función cuando Detalle era una Activity
     * aparte. Empareja "vista" (la tarjeta, marcada con este nombre por
     * MiReporteAdapter) con la vista que DetalleFragment marca con el mismo
     * nombre al entrar (ver DetalleFragment.onViewReady).
     */
    private fun abrirDetalle(vista: View, r: MiReporte) {
        val nombreTransicion = "reporte_${r.ticket}"
        findNavController().navigate(
            R.id.action_reportes_a_detalle,
            Bundle().apply { putString("ticket", r.ticket) },
            null,
            FragmentNavigatorExtras(vista to nombreTransicion)
        )
    }

    /**
     * Abre el flujo de reporte (grafo nav_reporte, FASE 4) desde el estado
     * vacío, igual que el FAB "Reportar" de MainActivity. Se navega por id, no
     * por <action>: un grafo incluido con <include> no puede referenciarse
     * desde una <action> declarada en OTRO archivo de navegación (ver el
     * comentario de cabecera de nav_reporte.xml), pero sí es un destino
     * navegable válido en tiempo de ejecución una vez que nav_ciudadano.xml lo
     * incluye.
     */
    private fun abrirCaptura() {
        findNavController().navigate(R.id.nav_reporte)
    }

    override fun onBindingDestroy() {
        binding.rvReportes.adapter = null
    }
}
