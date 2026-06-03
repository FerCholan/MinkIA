package com.moviles.minkia.ui.formulario

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Reporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.ActivityFormularioBinding
import com.moviles.minkia.ui.confirmacion.ConfirmacionActivity
import java.io.File

/**
 * Formulario del reporte (mockup C11). Toma el resultado del análisis, la
 * ubicación GPS y los datos que completa el ciudadano, y arma el reporte para
 * enviarlo (offline-first). La Activity observa el guardado y navega a la
 * confirmación.
 */
class FormularioActivity : BaseActivity<ActivityFormularioBinding>() {

    private val viewModel: ReporteViewModel by viewModels { ReporteViewModel.Factory() }

    private var rutaFoto: String? = null
    private var latitud = CHIMBOTE_LAT
    private var longitud = CHIMBOTE_LNG
    private var direccion = "Chimbote, Áncash"

    override fun inflateBinding(inflater: LayoutInflater) = ActivityFormularioBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetSuperior(binding.toolbar)
        aplicarInsetInferior(binding.formularioRoot) // el botón Enviar esquiva la barra de gestos
        barrasCabeceraVerde() // toolbar verde arriba, fondo claro abajo
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnReanalizar.setOnClickListener { finish() }

        mostrarAnalisis()
        configurarTipo()
        seleccionarSeveridadInicial()
        obtenerUbicacion()
        observar()

        binding.btnEnviar.setOnClickListener { enviar() }
    }

    private fun mostrarAnalisis() {
        rutaFoto = intent.getStringExtra(EXTRA_FOTO)
        rutaFoto?.let { binding.ivThumb.setImageURI(Uri.fromFile(File(it))) }

        val tipo = intent.getStringExtra(EXTRA_TIPO) ?: "Basura"
        val confianza = intent.getIntExtra(EXTRA_CONFIANZA, 0)
        val area = intent.getDoubleExtra(EXTRA_AREA, 0.0)

        binding.tvDeteccion.text = getString(R.string.form_deteccion, tipo, area.toString())
        binding.chipIa.text = getString(R.string.form_ia, confianza)
    }

    private fun configurarTipo() {
        val tipos = resources.getStringArray(R.array.tipos_residuo)
        binding.dropdownTipo.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, tipos)
        )
        binding.dropdownTipo.setText(tipos.first(), false)
    }

    private fun seleccionarSeveridadInicial() {
        val sev = runCatching {
            Severidad.valueOf(intent.getStringExtra(EXTRA_SEVERIDAD) ?: "")
        }.getOrDefault(Severidad.MEDIA)

        val id = when (sev) {
            Severidad.ALTA -> R.id.sevAlta
            Severidad.MEDIA -> R.id.sevMedia
            Severidad.BAJA -> R.id.sevBaja
        }
        binding.grupoSeveridad.check(id)
    }

    private fun severidadSeleccionada(): Severidad = when (binding.grupoSeveridad.checkedButtonId) {
        R.id.sevAlta -> Severidad.ALTA
        R.id.sevBaja -> Severidad.BAJA
        else -> Severidad.MEDIA
    }

    /** Ubicación best-effort con LocationManager (sin Play Services). */
    private fun obtenerUbicacion() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                latitud = loc.latitude
                longitud = loc.longitude
                binding.chipPreciso.setText(R.string.form_ubicacion_preciso)
                resolverDireccion(latitud, longitud)
            } else {
                binding.chipPreciso.setText(R.string.form_ubicacion_aprox)
            }
        } catch (e: SecurityException) {
            binding.chipPreciso.setText(R.string.form_ubicacion_aprox)
        }
        binding.tvDireccion.text = direccion
        binding.tvCoords.text = "%.4f, %.4f".format(latitud, longitud)
    }

    private fun resolverDireccion(lat: Double, lng: Double) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(this).getFromLocation(lat, lng, 1)?.firstOrNull()
        }.getOrNull()?.let { dir ->
            direccion = listOfNotNull(dir.thoroughfare, dir.subLocality, dir.locality)
                .firstOrNull() ?: direccion
        }
    }

    private fun observar() {
        viewModel.estado.observe(this) { estado ->
            when (estado) {
                is UiState.Loading -> cargando(true)
                is UiState.Success -> irAConfirmacion(estado.data)
                is UiState.Error -> {
                    cargando(false)
                    Toast.makeText(this, estado.mensaje, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun enviar() {
        viewModel.enviar(
            tipo = binding.dropdownTipo.text?.toString().orEmpty(),
            severidad = severidadSeleccionada(),
            descripcion = binding.etDescripcion.text?.toString().orEmpty(),
            direccion = direccion,
            latitud = latitud,
            longitud = longitud,
            fotoPath = rutaFoto
        )
    }

    private fun cargando(activo: Boolean) {
        binding.btnEnviar.isEnabled = !activo
        binding.btnEnviar.setText(if (activo) R.string.form_enviando else R.string.form_enviar)
    }

    private fun irAConfirmacion(reporte: Reporte) {
        startActivity(
            Intent(this, ConfirmacionActivity::class.java)
                .putExtra(ConfirmacionActivity.EXTRA_TICKET, reporte.ticket)
        )
        finishAffinity() // cierra el flujo de reporte; la confirmación queda arriba.
    }

    companion object {
        const val EXTRA_FOTO = "extra_foto"
        const val EXTRA_TIPO = "extra_tipo"
        const val EXTRA_CONFIANZA = "extra_confianza"
        const val EXTRA_SEVERIDAD = "extra_severidad"
        const val EXTRA_AREA = "extra_area"

        private const val CHIMBOTE_LAT = -9.0745
        private const val CHIMBOTE_LNG = -78.5936
    }
}
