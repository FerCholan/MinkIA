package com.moviles.minkia.ui.detalle

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.transition.MaterialContainerTransform
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.FragmentDetalleBinding

/**
 * Detalle y seguimiento de un reporte (mockup C15), destino de nav_ciudadano. Se
 * llega desde Reportes (tarjeta con transición compartida) o desde el Mapa (un
 * marcador, sin transición: nunca la tuvo).
 *
 * FASE 3 (matar los extras): DetalleActivity recibía el reporte COMPLETO por 11
 * extras, uno por campo. Ahora el único dato que cruza la navegación es
 * [ARG_REPORTE_ID] (ver nav_ciudadano.xml) y esta pantalla le pide el reporte entero
 * a [DetalleViewModel]. Por eso, aunque el resto de esta fase reutiliza
 * ViewModels que ya existían, acá se CREÓ uno nuevo: la Activity original no
 * tenía ninguno (pintaba directo lo que llegaba por Intent, sin tocar datos), y
 * ahora por primera vez la pantalla necesita ir a buscar los suyos.
 */
class DetalleFragment : BaseFragment<FragmentDetalleBinding>() {

    private val reporteId: String by lazy { requireArguments().getString(ARG_REPORTE_ID).orEmpty() }
    private val viewModel: DetalleViewModel by viewModels { DetalleViewModel.Factory(reporteId) }

    // MiReporteAdapter marca la tarjeta tocada con "reporte_<id>" (el mismo id que
    // viaja como argumento): se recalcula acá en vez de mandarlo como segundo
    // argumento, porque ya alcanza con el id para reproducirlo. Va por id y no por
    // ticket justamente porque el nombre de una transición compartida tiene que ser
    // ÚNICO en pantalla, y los tickets pueden repetirse.
    private val nombreTransicion: String by lazy { "reporte_$reporteId" }

    /**
     * La transición compartida se arma en onCreate (antes de que exista la vista),
     * como pide la API de Fragment. Es el equivalente fragment-a-fragment de
     * DetalleActivity.configurarTransicionCompartida: allá se aplicaba sobre la
     * window (Activity-a-Activity, vía ActivityOptionsCompat); acá se aplica
     * sobre el propio fragment (Navigation Component arma el emparejamiento vía
     * FragmentNavigatorExtras, ver ReportesFragment.abrirDetalle). Queda montada
     * SIEMPRE, incluso al entrar desde el Mapa: si nadie del lado de origen
     * ofrece un elemento compartido con el mismo nombre, esto simplemente no
     * tiene con qué emparejar y no se ve ninguna animación de más.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sin setEnterSharedElementCallback: ese callback (que sí usaba
        // DetalleActivity) solo existe en el paquete `transition.platform` de
        // Material, porque extiende android.app.SharedElementCallback y sirve
        // para transiciones entre Activities. Entre fragments el emparejamiento
        // de nombres lo resuelve FragmentNavigatorExtras, así que alcanza con
        // declarar las transiciones de abajo. Por el mismo motivo, las clases se
        // importan de `material.transition` y no de `material.transition.platform`:
        // esta versión trabaja sobre androidx.transition, que es la que usan los
        // fragments.
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            duration = 340
            scrimColor = Color.TRANSPARENT
        }
        sharedElementReturnTransition = MaterialContainerTransform().apply {
            duration = 300
            scrimColor = Color.TRANSPARENT
        }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentDetalleBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerDetalle.aplicarInsetSuperior()
        // La cabecera verde ya la fija MainActivity UNA sola vez para todo el
        // host (ver MainActivity.onViewReady): a diferencia de nav_auth, acá
        // ninguna pantalla necesita volver a pedirla.
        ViewCompat.setTransitionName(binding.detalleRoot, nombreTransicion)
        // popBackStack reproduce la animación de vuelta (contracción) con la
        // sharedElementReturnTransition de arriba; reemplaza a
        // supportFinishAfterTransition, que era la versión Activity de lo mismo.
        binding.btnVolver.setOnClickListener { findNavController().popBackStack() }
        binding.btnVerMapa.setOnClickListener { irAlMapa() }

        // Sin skeleton propio (el layout nunca tuvo uno: antes pintaba sync desde
        // extras). Mientras carga se oculta el scroll entero, igual que hace
        // NotificacionesFragment con el suyo.
        viewModel.uiState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> binding.scrollDetalle.visibility = View.GONE
                is UiState.Success -> {
                    binding.scrollDetalle.visibility = View.VISIBLE
                    pintar(estado.data)
                }
                is UiState.Error -> {
                    binding.scrollDetalle.visibility = View.GONE
                    Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun pintar(r: MiReporte) {
        binding.tvAutor.text = if (r.autor.isBlank()) getString(R.string.detalle_autor_anonimo)
        else getString(R.string.detalle_autor_fmt, r.autor)
        binding.ivAutorNivel.setImageResource(insigniaNivel(r.autorNivel))

        binding.tvTitulo.text = getString(R.string.detalle_titulo, r.ticket)
        binding.fotoReporte.permitirAmpliar = true
        binding.fotoReporte.cargar(r.fotoUrl)
        // IA REAL del reporte (tipo + confianza detectados), no hardcodeado.
        val tipo = r.tipo.ifBlank { "Basura" }
        binding.chipIa.text = getString(R.string.detalle_ia, tipo, r.confianza)
        binding.chipArea.text = getString(R.string.detalle_area, r.porcentajeCobertura)

        binding.chipSeveridad.text = r.severidad.name
        binding.chipSeveridad.setTextColor(ContextCompat.getColor(requireContext(), colorSeveridad(r.severidad)))

        // El chip y el banner de vecinos solo tienen sentido si hay agrupación
        // real. Si Detalle se abrió desde el Mapa, r.vecinos siempre es 0 (ese
        // dato nunca viajó desde ahí, ver DetalleViewModel/CiudadanoRepository):
        // mismo comportamiento que ya tenía la pantalla antes del refactor.
        if (r.vecinos > 0) {
            binding.chipVecinos.visibility = View.VISIBLE
            binding.tvUnion.visibility = View.VISIBLE
            binding.chipVecinos.text = getString(R.string.detalle_vecinos, r.vecinos)
            binding.tvUnion.text = getString(R.string.detalle_union, r.vecinos)
        } else {
            binding.chipVecinos.visibility = View.GONE
            binding.tvUnion.visibility = View.GONE
        }

        mostrarSeguimiento(r.estado)
    }

    /**
     * Muestra el seguimiento según el estado. En el flujo normal (recibido/en proceso/
     * resuelto) pinta el timeline. En las ramas terminales (duplicado/anulado) el
     * timeline NO aplica: se reemplaza por un cartel claro, para que el detalle no
     * contradiga al chip de la lista ni a la notificación.
     */
    private fun mostrarSeguimiento(estado: EstadoReporte) {
        val terminal = when (estado) {
            EstadoReporte.ANULADO -> R.string.detalle_anulado to R.color.naranja_terracota
            EstadoReporte.DUPLICADO -> R.string.detalle_duplicado to R.color.azul
            else -> null
        }
        if (terminal != null) {
            binding.cardTimeline.visibility = View.GONE
            binding.tvTerminal.visibility = View.VISIBLE
            binding.tvTerminal.setText(terminal.first)
            binding.tvTerminal.setTextColor(ContextCompat.getColor(requireContext(), terminal.second))
        } else {
            binding.tvTerminal.visibility = View.GONE
            binding.cardTimeline.visibility = View.VISIBLE
            pintarTimeline(estado)
        }
    }

    private fun pintarTimeline(estado: EstadoReporte) {
        // Flujo: Recibido (1) -> Validado/En proceso (2) -> Resuelto (3).
        // DUPLICADO y ANULADO son ramas terminales que no avanzaron: se quedan en 1.
        val activo = when (estado) {
            EstadoReporte.RECIBIDO, EstadoReporte.DUPLICADO, EstadoReporte.ANULADO -> 1
            EstadoReporte.EN_PROCESO -> 2
            EstadoReporte.RESUELTO -> 3
        }
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { idx, dot ->
            val paso = idx + 1
            pintarDot(
                dot,
                when {
                    paso < activo -> R.drawable.ic_check_chip
                    paso == activo && estado == EstadoReporte.RESUELTO -> R.drawable.ic_check_chip
                    paso == activo -> R.drawable.dot_activo
                    else -> R.drawable.dot_inactivo
                }
            )
        }
    }

    private fun pintarDot(dot: ImageView, recurso: Int) = dot.setImageResource(recurso)

    /** Drawable de la insignia según el nivel del autor (1..5). */
    private fun insigniaNivel(nivel: Int) = when (nivel.coerceIn(1, 5)) {
        1 -> R.drawable.nivel_1
        2 -> R.drawable.nivel_2
        3 -> R.drawable.nivel_3
        4 -> R.drawable.nivel_4
        else -> R.drawable.nivel_5
    }

    private fun colorSeveridad(s: Severidad) = when (s) {
        Severidad.ALTA -> R.color.sev_alta
        Severidad.MEDIA -> R.color.sev_media
        Severidad.BAJA -> R.color.sev_baja
    }

    /**
     * Antes relanzaba MainActivity con EXTRA_TAB=TAB_MAPA (Intent + flags), porque
     * DetalleActivity era una Activity aparte. Ese extra lo sigue necesitando
     * ConfirmacionActivity (todavía Activity, fuera de este host), así que
     * MainActivity.EXTRA_TAB no se tocó. Pero Detalle ahora VIVE dentro de
     * MainActivity: cambiar de pestaña es directo, mismo patrón que ya usa
     * HomeFragment.irAlMapa (asignar selectedItemId pasa por el mismo listener
     * que instaló BottomNavigationView.setupWithNavController).
     */
    private fun irAlMapa() {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.mapaFragment
    }

    companion object {
        /**
         * Único argumento de navegación: reemplaza a los once extras de
         * DetalleActivity. Es el id del documento, no el ticket visible.
         */
        const val ARG_REPORTE_ID = "reporteId"
    }
}
