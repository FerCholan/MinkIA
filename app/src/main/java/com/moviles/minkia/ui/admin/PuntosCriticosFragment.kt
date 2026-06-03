package com.moviles.minkia.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.databinding.FragmentAdminPuntosBinding

/**
 * Puntos críticos con agrupación espacial (mockup A03). Vista previa del mapa
 * con clusters por severidad y la lista de zonas. El mapa real se integra con el
 * SDK de Google Maps (clustering); el contrato visual ya queda hecho.
 */
class PuntosCriticosFragment : BaseFragment<FragmentAdminPuntosBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminPuntosBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerPuntosContenido.aplicarInsetSuperior()
    }
}
