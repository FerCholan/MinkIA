package com.moviles.minkia.ui.reportes

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.viewModels
import com.google.android.material.tabs.TabLayout
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.databinding.FragmentReportesBinding
import com.moviles.minkia.ui.captura.CapturaActivity
import com.moviles.minkia.ui.detalle.DetalleActivity

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
                is UiState.Error -> binding.skeletonReportes.root.mostrarSkeleton(false)
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

    private fun abrirDetalle(vista: View, r: MiReporte) {
        val nombreTransicion = "reporte_${r.ticket}"
        val intent = Intent(requireContext(), DetalleActivity::class.java)
            .putExtra(DetalleActivity.EXTRA_TICKET, r.ticket)
            .putExtra(DetalleActivity.EXTRA_DIRECCION, r.direccion)
            .putExtra(DetalleActivity.EXTRA_SEVERIDAD, r.severidad.name)
            .putExtra(DetalleActivity.EXTRA_AREA, r.areaM2)
            .putExtra(DetalleActivity.EXTRA_VECINOS, r.vecinos)
            .putExtra(DetalleActivity.EXTRA_ESTADO, r.estado.name)
            .putExtra(DetalleActivity.EXTRA_TRANSITION, nombreTransicion)
        // El reporte tocado es el elemento compartido: se expande hacia el detalle.
        val opciones = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(), vista, nombreTransicion
        )
        startActivity(intent, opciones.toBundle())
    }

    private fun abrirCaptura() {
        startActivity(Intent(requireContext(), CapturaActivity::class.java))
    }

    override fun onBindingDestroy() {
        binding.rvReportes.adapter = null
    }
}
