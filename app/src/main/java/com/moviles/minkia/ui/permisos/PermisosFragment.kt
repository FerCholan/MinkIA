package com.moviles.minkia.ui.permisos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetsVerticales
import com.moviles.minkia.core.barraEstadoOscura
import com.moviles.minkia.core.transicionFundido
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.databinding.FragmentPermisosBinding
import com.moviles.minkia.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Pantalla de permisos (mockup C06), destino del grafo nav_auth. Es una
 * pantalla de "priming": explica para qué se usa cada acceso y recién entonces
 * dispara los diálogos reales del sistema. No bloquea: el usuario puede
 * continuar aunque deniegue, porque cada permiso se vuelve a pedir en contexto
 * cuando haga falta (cámara al fotografiar, ubicación al geolocalizar).
 * Notificaciones es siempre opcional.
 */
class PermisosFragment : BaseFragment<FragmentPermisosBinding>() {

    // Las notificaciones de MinkIA son INTERNAS: se generan desde el estado de los
    // reportes y se leen dentro de la app (ver ui/notificaciones). No se publica
    // ninguna notificación del sistema, así que no se declara ni se pide
    // POST_NOTIFICATIONS: era un permiso que la app solicitaba y nunca usaba.

    // registerForActivityResult también existe en Fragment (fragment-ktx): se
    // registra igual, como propiedad, para quedar listo antes de que la
    // pantalla pueda necesitarlo (debe registrarse antes de CREATED).

    /** Tras pedir varios permisos, refresca los chips y entra a la app. */
    private val pedirYContinuar =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            actualizarChips()
            irAInicio()
        }

    /** Tras pedir un permiso suelto (tap en una tarjeta), solo refresca chips. */
    private val pedirIndividual =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            actualizarChips()
        }

    private val prefs by lazy { PreferenciasRepository.create(requireContext()) }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentPermisosBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.permisosRoot.aplicarInsetsVerticales()
        requireActivity().barraEstadoOscura() // fondo claro (edge-to-edge ya viene de BaseActivity)
        // Una vez mostrada la pantalla de priming, no se vuelve a mostrar en
        // próximos arranques. Se marca al abrirla (no al salir) para que la
        // escritura no compita con el finishAffinity() de irAInicio().
        lifecycleScope.launch { prefs.marcarPermisosVistos() }
        configurarAcciones()
    }

    override fun onResume() {
        super.onResume()
        // El usuario pudo cambiar permisos en Ajustes: reflejamos el estado real.
        actualizarChips()
    }

    private fun configurarAcciones() {
        binding.chipCamara.setOnClickListener { pedirSiFalta(Manifest.permission.CAMERA) }
        binding.chipUbicacion.setOnClickListener { pedirSiFalta(Manifest.permission.ACCESS_FINE_LOCATION) }
        // El chip de avisos no dispara ningún diálogo: no hay permiso que pedir.

        binding.btnContinuar.setOnClickListener {
            val pendientes = permisosRequeridos().filterNot { estaConcedido(it) }
            if (pendientes.isEmpty()) {
                irAInicio()
            } else {
                pedirYContinuar.launch(pendientes.toTypedArray())
            }
        }
    }

    /** Permisos reales que pide esta pantalla. */
    private fun permisosRequeridos(): List<String> =
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

    private fun pedirSiFalta(permiso: String) {
        if (!estaConcedido(permiso)) pedirIndividual.launch(arrayOf(permiso))
    }

    private fun estaConcedido(permiso: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permiso) == PackageManager.PERMISSION_GRANTED

    private fun actualizarChips() {
        pintarChip(binding.chipCamara, estaConcedido(Manifest.permission.CAMERA), opcional = false)
        pintarChip(binding.chipUbicacion, estaConcedido(Manifest.permission.ACCESS_FINE_LOCATION), opcional = false)
        // Los avisos de la app no dependen de un permiso del sistema: siempre están
        // disponibles porque se muestran dentro de la propia aplicación.
        pintarChip(binding.chipNotif, concedido = true, opcional = true)
    }

    /**
     * Pinta un chip según el estado: concedido -> "Permitido" (verde con check);
     * pendiente -> "Permitir" (terracota) u "Opcional" (gris) si no es obligatorio.
     */
    private fun pintarChip(chip: TextView, concedido: Boolean, opcional: Boolean) {
        val contexto = requireContext()
        if (concedido) {
            chip.setText(R.string.permisos_chip_permitido)
            chip.setBackgroundResource(R.drawable.bg_chip_permitido)
            chip.setTextColor(ContextCompat.getColor(contexto, R.color.verde_hoja))
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_chip, 0, 0, 0)
            chip.isClickable = false
        } else if (opcional) {
            chip.setText(R.string.permisos_chip_opcional)
            chip.setBackgroundResource(R.drawable.bg_chip_opcional)
            chip.setTextColor(ContextCompat.getColor(contexto, R.color.texto_secundario))
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            chip.isClickable = true
        } else {
            chip.setText(R.string.permisos_chip_permitir)
            chip.setBackgroundResource(R.drawable.bg_chip_permitir)
            chip.setTextColor(ContextCompat.getColor(contexto, R.color.naranja_terracota))
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            chip.isClickable = true
        }
    }

    /** Permisos es el último paso del flujo: siempre SALE del grafo hacia Inicio. */
    private fun irAInicio() {
        startActivity(Intent(requireContext(), MainActivity::class.java))
        requireActivity().transicionFundido()
        requireActivity().finishAffinity() // cierra todo el flujo de acceso: no se vuelve con "atrás".
    }
}
