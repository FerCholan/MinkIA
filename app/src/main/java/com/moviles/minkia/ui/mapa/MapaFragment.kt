package com.moviles.minkia.ui.mapa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.databinding.FragmentMapaBinding

/**
 * Explorar focos en el mapa (mockup C08). Vista previa estilizada con los focos
 * por severidad. El mapa interactivo real se integra con el SDK de Google Maps
 * (requiere API key); el contrato visual (header, filtros, pines) ya queda hecho.
 */
class MapaFragment : BaseFragment<FragmentMapaBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMapaBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerMapaContenido.aplicarInsetSuperior()
    }
}
