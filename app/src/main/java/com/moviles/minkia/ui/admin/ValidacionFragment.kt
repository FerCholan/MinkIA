package com.moviles.minkia.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetInferior
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.FragmentAdminValidacionBinding

/**
 * Detalle y moderación de un reporte para el administrador (A04), destino de
 * nav_admin. Muestra la foto y los datos REALES del reporte (Firestore):
 * dirección, COORDENADAS y HORA. Modera de verdad: aprobar resuelto (->
 * RESUELTO), validar (-> EN_PROCESO), cambiar el nivel (severidad), marcar
 * duplicado o anular (troll/falso, sin borrar). Las acciones necesitan rol
 * admin; si no, las reglas las rechazan y se avisa. Se abre desde la bandeja
 * (AlertasFragment).
 *
 * FASE 3 (matar los extras): ValidacionActivity recibía la alerta COMPLETA por
 * trece extras, uno por campo. Ahora el único dato que cruza la navegación es
 * [ARG_REPORTE_ID] (ver nav_admin.xml) y esta pantalla le pide la alerta entera
 * a [ValidacionViewModel] (que por eso gana [ValidacionViewModel.alerta]/
 * [ValidacionViewModel.cargar], su única parte nueva: antes no leía nada, solo
 * escribía las acciones de moderación).
 */
class ValidacionFragment : BaseFragment<FragmentAdminValidacionBinding>() {

    private val viewModel: ValidacionViewModel by viewModels { ValidacionViewModel.Factory() }
    private val reporteId: String by lazy { requireArguments().getString(ARG_REPORTE_ID).orEmpty() }
    private var severidad: Severidad = Severidad.MEDIA

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminValidacionBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.toolbar.aplicarInsetSuperior()
        binding.validacionRoot.aplicarInsetInferior() // los botones esquivan la barra de gestos
        // Cabecera verde: ya la fija MainActivity una sola vez para todo el host
        // (ver MainActivity.onViewReady); acá no hace falta repetirla.
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.btnResolver.setOnClickListener {
            confirmar(
                R.string.admin_val_resolver_titulo, R.string.admin_val_resolver_mensaje, R.string.admin_val_resolver
            ) { viewModel.resolver(reporteId) }
        }
        binding.btnValidar.setOnClickListener {
            confirmar(
                R.string.admin_val_validar_titulo, R.string.admin_val_validar_mensaje, R.string.admin_val_validar
            ) { viewModel.validar(reporteId) }
        }
        binding.btnDuplicado.setOnClickListener {
            confirmar(
                R.string.admin_val_duplicado_titulo, R.string.admin_val_duplicado_mensaje, R.string.admin_val_duplicado
            ) { viewModel.marcarDuplicado(reporteId) }
        }
        binding.btnNivel.setOnClickListener { elegirNivel() }
        binding.btnAnular.setOnClickListener {
            confirmar(
                R.string.admin_val_anular_titulo, R.string.admin_val_anular_mensaje, R.string.admin_val_anular
            ) { viewModel.anular(reporteId) }
        }

        observarViewModel()
        viewModel.cargar(reporteId)
    }

    /** Pinta los datos REALES de la alerta cargada por id (reemplaza el bloque que antes leía los trece extras). */
    private fun pintarAlerta(a: AlertaAdmin) {
        severidad = a.severidad
        val ticket = a.ticket.ifBlank { "#MK" }

        binding.fotoReporte.permitirAmpliar = true
        binding.fotoReporte.cargar(a.fotoUrl)
        binding.toolbar.title = getString(R.string.admin_val_titulo, ticket)
        // IA, área y tipo REALES del reporte (no hardcodeados).
        binding.chipIa.text = getString(R.string.admin_val_ia, a.tipo.ifBlank { "Basura" }, a.confianza)
        binding.chipArea.text = getString(R.string.admin_val_area, a.porcentajeCobertura)
        pintarSeveridad()
        binding.chipReportes.text = getString(R.string.detalle_vecinos, a.agrupados)
        binding.tvUbicacion.text = a.direccion.ifBlank { "—" }
        binding.tvCoordenadas.text = getString(R.string.admin_val_coord_fmt, a.latitud, a.longitud)
        binding.tvFechaHora.text = a.fechaHoraTexto
        binding.tvEstado.text = a.estado.etiqueta
        binding.tvTipo.text = a.tipo
        binding.tvReportado.text = getString(R.string.detalle_vecinos, a.agrupados)

        // Descripción del vecino: solo si el reporte trae texto.
        if (a.descripcion.isNotBlank()) {
            binding.bloqueDescripcion.visibility = View.VISIBLE
            binding.tvDescripcion.text = a.descripcion
        } else {
            binding.bloqueDescripcion.visibility = View.GONE
        }

        // Banner de unificación: solo tiene sentido si hay más de un reporte agrupado.
        if (a.agrupados > 1) {
            binding.tvUnificados.visibility = View.VISIBLE
            binding.tvUnificados.text = getString(R.string.admin_val_unificados, a.agrupados)
        } else {
            binding.tvUnificados.visibility = View.GONE
        }
    }

    /**
     * Pide confirmación antes de una acción de moderación (todas la piden). Solo si
     * el admin acepta, se dispara contra Firestore vía el ViewModel. Evita cambios
     * por toque accidental.
     */
    private fun confirmar(
        tituloRes: Int,
        mensajeRes: Int,
        positivoRes: Int,
        accion: () -> Unit
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(tituloRes)
            .setMessage(mensajeRes)
            .setPositiveButton(positivoRes) { _, _ -> accion() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Diálogo para elegir el nuevo nivel (severidad). Al confirmar, actualiza sin salir. */
    private fun elegirNivel() {
        val niveles = arrayOf(Severidad.ALTA, Severidad.MEDIA, Severidad.BAJA)
        val etiquetas = niveles.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.admin_val_nivel_titulo)
            .setSingleChoiceItems(etiquetas, niveles.indexOf(severidad)) { dialog, cual ->
                dialog.dismiss()
                cambiarNivel(niveles[cual])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Dispara el cambio de nivel en el ViewModel; el chip se repinta al éxito. */
    private fun cambiarNivel(nuevo: Severidad) {
        if (reporteId.isBlank() || nuevo == severidad) return
        viewModel.actualizarNivel(reporteId, nuevo)
    }

    private fun pintarSeveridad() {
        binding.chipSeveridad.text = getString(R.string.reportes_severidad, severidad.name)
        binding.chipSeveridad.setTextColor(ContextCompat.getColor(requireContext(), colorSev(severidad)))
    }

    /**
     * Observa las TRES salidas del ViewModel. [ValidacionViewModel.alerta] es la
     * carga inicial (nueva en esta fase, ver KDoc de la clase): mientras no
     * resuelva, el contenido queda oculto. [ValidacionViewModel.uiState] cubre
     * las cuatro acciones terminales (resolver/validar/duplicado/anular): en
     * éxito la pantalla siempre vuelve atrás, solo cambia el texto del aviso
     * según [AccionModeracion]. Cambiar el nivel no cierra la pantalla, por eso
     * usa su propio LiveData ([ValidacionViewModel.nivelState]).
     */
    private fun observarViewModel() {
        viewModel.alerta.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    binding.scrollValidacion.visibility = View.GONE
                    habilitarBotones(false)
                }
                is UiState.Success -> {
                    binding.scrollValidacion.visibility = View.VISIBLE
                    pintarAlerta(estado.data)
                    habilitarBotones(true)
                }
                is UiState.Error -> {
                    binding.scrollValidacion.visibility = View.GONE
                    Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                }
            }
        }
        viewModel.uiState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> habilitarBotones(false)
                is UiState.Success -> {
                    Toast.makeText(requireContext(), mensajeOk(estado.data), Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                // Típicamente falta de rol admin: avisa sin cerrar, para reintentar.
                is UiState.Error -> {
                    Toast.makeText(requireContext(), R.string.admin_val_error, Toast.LENGTH_LONG).show()
                    habilitarBotones(true)
                }
            }
        }
        viewModel.nivelState.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> habilitarBotones(false)
                is UiState.Success -> {
                    severidad = estado.data
                    pintarSeveridad()
                    habilitarBotones(true)
                    Toast.makeText(requireContext(), R.string.admin_val_nivel_ok, Toast.LENGTH_SHORT).show()
                }
                is UiState.Error -> {
                    Toast.makeText(requireContext(), R.string.admin_val_error, Toast.LENGTH_LONG).show()
                    habilitarBotones(true)
                }
            }
        }
    }

    private fun mensajeOk(accion: AccionModeracion) = when (accion) {
        AccionModeracion.RESOLVER -> R.string.admin_val_resuelto_ok
        AccionModeracion.VALIDAR -> R.string.admin_val_validado_ok
        AccionModeracion.DUPLICADO -> R.string.admin_val_duplicado_ok
        AccionModeracion.ANULAR -> R.string.admin_val_anulado_ok
    }

    private fun habilitarBotones(habilitado: Boolean) {
        binding.btnResolver.isEnabled = habilitado
        binding.btnValidar.isEnabled = habilitado
        binding.btnNivel.isEnabled = habilitado
        binding.btnDuplicado.isEnabled = habilitado
        binding.btnAnular.isEnabled = habilitado
    }

    private fun colorSev(s: Severidad) = when (s) {
        Severidad.ALTA -> R.color.sev_alta
        Severidad.MEDIA -> R.color.sev_media
        Severidad.BAJA -> R.color.sev_baja
    }

    companion object {
        /** Único argumento de navegación: reemplaza a los trece extras de ValidacionActivity. */
        const val ARG_REPORTE_ID = "reporteId"
    }
}
