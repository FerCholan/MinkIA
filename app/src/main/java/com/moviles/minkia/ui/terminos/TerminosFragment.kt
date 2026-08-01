package com.moviles.minkia.ui.terminos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetInferior
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.barrasCabeceraVerde
import com.moviles.minkia.databinding.FragmentTerminosBinding

/**
 * Términos y Condiciones + Política de Datos (pantalla leíble), destino del
 * grafo nav_auth. Se abre desde los enlaces del checkbox del registro y, a
 * futuro, desde "Acerca de" en Configuración. Solo muestra contenido; sin
 * lógica de negocio.
 */
class TerminosFragment : BaseFragment<FragmentTerminosBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentTerminosBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        // Insets e íconos de barra: antes los resolvía BaseActivity una vez por
        // pantalla; ahora cada fragment los pide al entrar, porque comparte la
        // window de AuthActivity con el resto del flujo de acceso.
        binding.toolbar.aplicarInsetSuperior()
        binding.terminosRoot.aplicarInsetInferior() // el último párrafo esquiva la barra de gestos
        requireActivity().barrasCabeceraVerde() // cabecera verde arriba, fondo claro abajo
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }
}
