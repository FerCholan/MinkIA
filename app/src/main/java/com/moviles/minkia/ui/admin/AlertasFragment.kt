package com.moviles.minkia.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.tabs.TabLayout
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.mostrarSkeleton
import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.databinding.FragmentAdminAlertasBinding

/**
 * Bandeja de reportes entrantes del administrador (mockup A02). Lista las
 * alertas, filtra por pestaña y abre el detalle/validación al tocar una.
 */
class AlertasFragment : BaseFragment<FragmentAdminAlertasBinding>() {

    private val viewModel: AlertasViewModel by viewModels { AlertasViewModel.Factory() }
    private val adapter = AlertaAdapter { abrirValidacion(it) }

    private var todas: List<AlertaAdmin> = emptyList()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminAlertasBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerAlertasContenido.aplicarInsetSuperior()
        binding.rvAlertas.adapter = adapter

        binding.tabs.apply {
            addTab(newTab().setText(R.string.admin_alertas_tab_nuevos))
            addTab(newTab().setText(R.string.admin_alertas_tab_sin))
            addTab(newTab().setText(R.string.admin_alertas_tab_urgentes))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = filtrar(tab.position)
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }

        viewModel.uiState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    binding.skeletonAlertas.root.mostrarSkeleton(true)
                    binding.rvAlertas.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.skeletonAlertas.root.mostrarSkeleton(false)
                    binding.rvAlertas.visibility = View.VISIBLE
                    todas = estado.data
                    filtrar(binding.tabs.selectedTabPosition.coerceAtLeast(0))
                }
                is UiState.Error -> {
                    binding.skeletonAlertas.root.mostrarSkeleton(false)
                    binding.rvAlertas.visibility = View.VISIBLE
                }
            }
        }
    }

    /** 0 = Nuevos, 1 = Sin asignar, 2 = Urgentes. */
    private fun filtrar(posicion: Int) {
        val lista = when (posicion) {
            0 -> todas.filter { it.nuevo }
            1 -> todas.filter { !it.nuevo }
            2 -> todas.filter { it.severidad == com.moviles.minkia.data.model.Severidad.ALTA }
            else -> todas
        }
        adapter.submitList(lista) { binding.rvAlertas.scheduleLayoutAnimation() }
    }

    private fun abrirValidacion(a: AlertaAdmin) {
        startActivity(
            Intent(requireContext(), ValidacionActivity::class.java)
                .putExtra(ValidacionActivity.EXTRA_DIRECCION, a.direccion)
                .putExtra(ValidacionActivity.EXTRA_SEVERIDAD, a.severidad.name)
                .putExtra(ValidacionActivity.EXTRA_AGRUPADOS, a.agrupados)
        )
    }

    override fun onBindingDestroy() {
        binding.rvAlertas.adapter = null
    }
}
