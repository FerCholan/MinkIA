package com.moviles.minkia.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.recortarEsquinasInferiores
import com.moviles.minkia.databinding.FragmentAdminRutasBinding

/**
 * Planificación de la ruta de recolección (mockup A05). Muestra la ruta
 * optimizada, sus paradas por prioridad y el camión asignable. Datos
 * representativos; la optimización real corre en el backend.
 */
class RutasFragment : BaseFragment<FragmentAdminRutasBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminRutasBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerRutasContenido.aplicarInsetSuperior()
        binding.headerRutas.recortarEsquinasInferiores()
        binding.btnEnviarRuta.setOnClickListener {
            Toast.makeText(requireContext(), R.string.admin_rutas_enviar, Toast.LENGTH_SHORT).show()
        }
    }
}
