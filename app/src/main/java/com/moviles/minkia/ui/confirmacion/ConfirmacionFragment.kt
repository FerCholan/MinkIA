package com.moviles.minkia.ui.confirmacion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetsVerticales
import com.moviles.minkia.core.barraEstadoOscura
import com.moviles.minkia.core.vibrar
import com.moviles.minkia.databinding.FragmentConfirmacionBinding
import com.moviles.minkia.ui.reporte.ReporteFlowViewModel

/**
 * Confirmación y sincronización del reporte (mockup C12), último paso del
 * grafo nav_reporte. Muestra el ticket que dejó el [ReporteFlowViewModel]
 * compartido al guardar (ver FormularioFragment / ReporteFlowViewModel.enviar)
 * y el estado del seguimiento. No aporta datos nuevos al flujo: su única
 * responsabilidad es cerrarlo.
 */
class ConfirmacionFragment : BaseFragment<FragmentConfirmacionBinding>() {

    private val flujo: ReporteFlowViewModel by navGraphViewModels(R.id.nav_reporte) {
        ReporteFlowViewModel.Factory(requireContext().applicationContext)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentConfirmacionBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.confirmacionRoot.aplicarInsetsVerticales()
        requireActivity().barraEstadoOscura() // fondo claro

        binding.tvTicket.text = flujo.reporte?.ticket.orEmpty()
        mostrarEstadoSincronizacion()

        animarExito()

        binding.btnVolverInicio.setOnClickListener { cerrarFlujo(R.id.homeFragment) }
        binding.btnVerMapa.setOnClickListener { cerrarFlujo(R.id.mapaFragment) }
    }

    /**
     * Ajusta el mensaje según dónde quedó REALMENTE el reporte. Si todavía está en
     * la cola local, no se puede afirmar que "llegó al equipo de gestión": se avisa
     * que se enviará solo al recuperar la conexión. El ticket es válido en ambos
     * casos, así que el vecino puede hacer seguimiento igual.
     */
    private fun mostrarEstadoSincronizacion() {
        if (!flujo.pendienteDeSincronizar) return
        binding.tvConfTitulo.setText(R.string.conf_titulo_pendiente)
        binding.tvConfSubtitulo.setText(R.string.conf_subtitulo_pendiente)
        binding.tvConfSincronizacion.setText(R.string.conf_pendiente)
    }

    /** Momento de logro: el check "explota" suave con un rebote y una vibración. */
    private fun animarExito() {
        binding.ivCheck.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0f
            animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setInterpolator(OvershootInterpolator())
                .setDuration(480)
                .withEndAction { vibrar() }
                .start()
        }
    }

    /**
     * Cierra el flujo de reporte y deja seleccionada la pestaña del ciudadano
     * que corresponda ([idDestinoCiudadano]: homeFragment o mapaFragment, los
     * dos destinos "de fábrica" de nav_ciudadano.xml).
     *
     * nav_reporte.xml es un grafo INDEPENDIENTE que nav_ciudadano.xml incluye
     * con <include>: no puede declarar <action> hacia homeFragment ni
     * mapaFragment porque esos ids no existen dentro del propio archivo
     * nav_reporte.xml (un grafo incluido se infla solo, sin ver los destinos
     * del grafo que lo incluye; ver el comentario de cabecera de
     * nav_reporte.xml). Por eso la salida se resuelve acá, en código, en dos
     * pasos:
     *
     * 1) popBackStack(R.id.nav_reporte, true): desapila TODO el flujo (sus
     *    cuatro fragments y el propio nodo nav_reporte) de una sola vez, y
     *    con él destruye a [flujo]. Es el equivalente Navigation Component
     *    del startActivity(MainActivity, CLEAR_TASK) + finishAffinity() que
     *    usaba la vieja ConfirmacionActivity para que "atrás" no pudiera
     *    volver a Formulario/Análisis/Captura.
     * 2) Selecciona el ítem del BottomNavigationView de MainActivity, buscado
     *    por id (este fragment no conoce MainActivity ni nav_ciudadano.xml,
     *    ni le corresponde: son del grafo/Activity que lo aloja). Asignar
     *    selectedItemId dispara el mismo listener que un toque real en la
     *    pestaña (setupWithNavController ya lo conectó en
     *    MainActivity.configurarGrafoSegunRol), con su propio popUpTo al
     *    inicio de nav_ciudadano: es el mismo mecanismo que hoy usa
     *    MainActivity para el EXTRA_TAB que mandaba la vieja
     *    ConfirmacionActivity, aplicado directamente en vez de leído de un
     *    Intent.
     */
    private fun cerrarFlujo(idDestinoCiudadano: Int) {
        findNavController().popBackStack(R.id.nav_reporte, true)
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav?.selectedItemId = idDestinoCiudadano
    }
}
