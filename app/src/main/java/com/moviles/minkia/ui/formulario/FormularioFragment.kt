package com.moviles.minkia.ui.formulario

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import coil.load
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetInferior
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.barrasCabeceraVerde
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.FragmentFormularioBinding
import com.moviles.minkia.ui.reporte.ReporteFlowViewModel
import java.io.File

/**
 * Formulario del reporte (mockup C11), tercer paso del grafo nav_reporte. Lee
 * la foto y el resultado del análisis desde el [ReporteFlowViewModel]
 * compartido, obtiene la ubicación GPS y, con lo que completa el ciudadano,
 * arma el envío (offline-first). Al guardar, el reporte con su ticket queda
 * en el mismo ViewModel y este fragment navega a la confirmación.
 */
class FormularioFragment : BaseFragment<FragmentFormularioBinding>() {

    private val flujo: ReporteFlowViewModel by navGraphViewModels(R.id.nav_reporte) {
        ReporteFlowViewModel.Factory(requireContext().applicationContext)
    }

    private var latitud = CHIMBOTE_LAT
    private var longitud = CHIMBOTE_LNG
    private var direccion = "Chimbote, Áncash"
    private var zona = "Chimbote" // barrio/distrito real (Geocoder), para agrupar en el mapa

    // La ubicación es OBLIGATORIA: sin un fix real no se puede enviar (un reporte
    // sin ubicación no le sirve a nadie). El botón Enviar queda bloqueado hasta que
    // haya coordenadas de verdad.
    private var ubicacionLista = false
    private var mapaUbi: GoogleMap? = null // mapa lite que muestra el pin de la foto

    /** Pide el permiso de ubicación en contexto. Si lo dan, obtiene la ubicación. */
    private val pedirUbicacion =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { concedidos ->
            val ok = concedidos[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                concedidos[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (ok) obtenerUbicacion() else bloquearEnvio(EstadoUbi.SIN_PERMISO)
        }

    /**
     * Recibe el primer fix fresco cuando no había uno cacheado. Objeto explícito (no
     * lambda) porque en API < 30 los métodos de LocationListener eran abstractos: un
     * lambda crashearía con AbstractMethodError al llamarlos. minSdk = 24.
     */
    private val listenerUbi = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            detenerActualizaciones()
            usarUbicacion(loc)
        }

        @Deprecated("Requerido en API < 30", ReplaceWith(""))
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    /** Corta la espera del fix si tarda demasiado y avisa que no se pudo. */
    private val timeoutUbi = Runnable {
        detenerActualizaciones()
        if (!ubicacionLista) bloquearEnvio(EstadoUbi.NO_DISPONIBLE)
    }

    /** Motivo por el que el envío está bloqueado, para mostrar el mensaje justo. */
    private enum class EstadoUbi { OBTENIENDO, SIN_PERMISO, SERVICIO_OFF, NO_DISPONIBLE }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentFormularioBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.toolbar.aplicarInsetSuperior()
        binding.formularioRoot.aplicarInsetInferior() // el botón Enviar esquiva la barra de gestos
        requireActivity().barrasCabeceraVerde() // toolbar verde arriba, fondo claro abajo
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.btnReanalizar.setOnClickListener { findNavController().popBackStack() }

        mostrarAnalisis()
        configurarTipo()
        seleccionarSeveridadInicial()
        observar()

        // Arranca bloqueado: hasta tener ubicación real, no se envía.
        bloquearEnvio(EstadoUbi.OBTENIENDO)
        asegurarUbicacion()
        prepararMapa()

        binding.btnEnviar.setOnClickListener { enviar() }
    }

    /**
     * Prepara el mapa lite; cuando esté listo, dibuja el pin de la ubicación
     * actual. childFragmentManager, NO supportFragmentManager (que ni existe
     * en un Fragment): el FragmentContainerView con SupportMapFragment del
     * layout de ESTE fragment se infla como hijo de ESTE fragment, no de la
     * Activity que lo aloja.
     */
    private fun prepararMapa() {
        (childFragmentManager.findFragmentById(R.id.mapaUbicacion) as? SupportMapFragment)
            ?.getMapAsync { mapa ->
                mapaUbi = mapa
                mapa.uiSettings.isMapToolbarEnabled = false
                actualizarMapa()
            }
    }

    /** Centra el mapa en la ubicación de la foto y pone el pin ahí. */
    private fun actualizarMapa() {
        val mapa = mapaUbi ?: return
        val pos = LatLng(latitud, longitud)
        mapa.clear()
        mapa.addMarker(MarkerOptions().position(pos))
        mapa.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
    }

    /** Si ya hay permiso, obtiene la ubicación; si no, la pide en contexto. */
    private fun asegurarUbicacion() {
        if (tienePermisoUbicacion()) {
            obtenerUbicacion()
        } else {
            bloquearEnvio(EstadoUbi.OBTENIENDO)
            pedirUbicacion.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun tienePermisoUbicacion(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Pinta la foto y la detección: ambas ya están en el ViewModel compartido
     *  (fotoPath desde Captura, resultadoAnalisis desde Análisis). */
    private fun mostrarAnalisis() {
        // Coil decodifica en segundo plano y submuestrea al tamaño del thumb (64dp):
        // no bloquea el hilo principal ni aloca el bitmap full-res del sensor.
        flujo.fotoPath?.let { binding.ivThumb.load(File(it)) }

        val resultado = flujo.resultadoAnalisis
        val tipo = resultado?.tipo ?: "Basura"
        val areaM2 = resultado?.areaM2 ?: 0.0

        binding.tvDeteccion.text = getString(R.string.form_deteccion, tipo, areaM2.toString())
        binding.chipIa.text = getString(R.string.form_ia, resultado?.confianza ?: 0)
    }

    private fun configurarTipo() {
        val tipos = resources.getStringArray(R.array.tipos_residuo)
        binding.dropdownTipo.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, tipos)
        )
        binding.dropdownTipo.setText(tipos.first(), false)
    }

    private fun seleccionarSeveridadInicial() {
        val sev = flujo.resultadoAnalisis?.severidad ?: Severidad.MEDIA
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

    /**
     * Ubicación OBLIGATORIA con LocationManager. Verifica que el servicio esté
     * encendido, usa el último fix si existe y, si no, pide uno fresco (con timeout).
     * Solo con un fix real se desbloquea el envío.
     */
    private fun obtenerUbicacion() {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val red = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gps && !red) { bloquearEnvio(EstadoUbi.SERVICIO_OFF); return }

        try {
            val ultima = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            // Solo aceptamos el fix cacheado si es RECIENTE: uno viejo (de otra salida,
            // de horas atrás) geoetiquetaría el reporte en un lugar equivocado. Si no
            // sirve, caemos a pedir uno fresco (rama de abajo).
            if (ultima != null && esFixReciente(ultima)) {
                usarUbicacion(ultima)
                return
            }
            // No hay fix cacheado: pedimos uno fresco y esperamos (con timeout).
            bloquearEnvio(EstadoUbi.OBTENIENDO)
            val proveedor = if (gps) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            lm.requestLocationUpdates(proveedor, 0L, 0f, listenerUbi, Looper.getMainLooper())
            binding.root.postDelayed(timeoutUbi, TIMEOUT_UBI_MS)
        } catch (e: SecurityException) {
            bloquearEnvio(EstadoUbi.SIN_PERMISO)
        }
    }

    /** Un fix cacheado sirve solo si no es más viejo que [MAX_EDAD_FIX_MS]. */
    private fun esFixReciente(loc: Location): Boolean {
        val edad = System.currentTimeMillis() - loc.time
        return edad in 0..MAX_EDAD_FIX_MS
    }

    /** Fija la ubicación obtenida, resuelve dirección/zona y DESBLOQUEA el envío. */
    private fun usarUbicacion(loc: Location) {
        latitud = loc.latitude
        longitud = loc.longitude
        resolverDireccion(latitud, longitud)
        actualizarMapa() // mueve el pin a la ubicación real de la foto
        binding.tvDireccion.text = direccion
        binding.tvCoords.text = "%.4f, %.4f".format(latitud, longitud)
        ubicacionLista = true
        binding.chipPreciso.setText(R.string.form_ubicacion_preciso)
        binding.chipPreciso.isClickable = false
        binding.chipPreciso.setOnClickListener(null)
        binding.btnEnviar.isEnabled = true
    }

    /**
     * Bloquea el envío y muestra en el chip el motivo, con una acción al tocarlo
     * (pedir permiso de nuevo, abrir ajustes de ubicación, o reintentar).
     */
    private fun bloquearEnvio(estado: EstadoUbi) {
        ubicacionLista = false
        binding.btnEnviar.isEnabled = false
        val texto: Int
        val accion: (() -> Unit)?
        when (estado) {
            EstadoUbi.OBTENIENDO -> { texto = R.string.form_ubicacion_obteniendo; accion = null }
            EstadoUbi.SIN_PERMISO -> { texto = R.string.form_ubicacion_permiso; accion = { abrirAjustesApp() } }
            EstadoUbi.SERVICIO_OFF -> { texto = R.string.form_ubicacion_desactivada; accion = { abrirAjustesUbicacion() } }
            EstadoUbi.NO_DISPONIBLE -> { texto = R.string.form_ubicacion_no_disp; accion = { asegurarUbicacion() } }
        }
        binding.chipPreciso.setText(texto)
        binding.chipPreciso.isClickable = accion != null
        binding.chipPreciso.setOnClickListener(accion?.let { a -> android.view.View.OnClickListener { a() } })
    }

    private fun detenerActualizaciones() {
        runCatching {
            (requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(listenerUbi)
        }
        binding.root.removeCallbacks(timeoutUbi)
    }

    /** Abre los ajustes de ubicación del sistema para que el usuario prenda el GPS. */
    private fun abrirAjustesUbicacion() {
        runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }

    /** Abre los ajustes de la app (para conceder el permiso si lo denegó fijo). */
    private fun abrirAjustesApp() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null))
            )
        }
    }

    private fun resolverDireccion(lat: Double, lng: Double) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(requireContext()).getFromLocation(lat, lng, 1)?.firstOrNull()
        }.getOrNull()?.let { dir ->
            // Dirección COMPLETA (calle + número + zona) si el geocoder la da; si no,
            // cae al nombre de la calle / barrio / ciudad, y por último al default.
            direccion = dir.getAddressLine(0)
                ?: listOfNotNull(dir.thoroughfare, dir.subLocality, dir.locality).firstOrNull()
                ?: direccion
            // Zona EXACTA desde los componentes estructurados: barrio (subLocality) si
            // existe, si no el distrito (locality: Chimbote vs Nuevo Chimbote). No se
            // adivina cortando la dirección: se toma el dato administrativo real.
            zona = listOfNotNull(dir.subLocality, dir.locality, dir.subAdminArea).firstOrNull() ?: zona
        }
    }

    private fun observar() {
        // viewLifecycleOwner: ver nota equivalente en AnalisisFragment.
        flujo.envio.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> cargando(true)
                is UiState.Success -> irAConfirmacion()
                is UiState.Error -> {
                    cargando(false)
                    Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun enviar() {
        // Guarda dura: sin ubicación real no se envía (obligatoria).
        if (!ubicacionLista) {
            Toast.makeText(requireContext(), R.string.form_ubicacion_obligatoria, Toast.LENGTH_LONG).show()
            asegurarUbicacion()
            return
        }
        flujo.enviar(
            tipo = binding.dropdownTipo.text?.toString().orEmpty(),
            severidad = severidadSeleccionada(),
            descripcion = binding.etDescripcion.text?.toString().orEmpty(),
            direccion = direccion,
            zona = zona,
            latitud = latitud,
            longitud = longitud,
            areaM2 = flujo.resultadoAnalisis?.areaM2 ?: 0.0,
            confianza = flujo.resultadoAnalisis?.confianza ?: 0
        )
    }

    private fun cargando(activo: Boolean) {
        binding.btnEnviar.isEnabled = !activo
        binding.btnEnviar.setText(if (activo) R.string.form_enviando else R.string.form_enviar)
    }

    /**
     * El ticket ya quedó en flujo.reporte (ver ReporteFlowViewModel.enviar);
     * Confirmación lo lee de ahí. La propia acción de navegación (ver
     * nav_reporte.xml, action_formulario_a_confirmacion) saca a
     * Captura/Análisis/Formulario del back stack con popUpTo: no se puede
     * volver a ellos una vez enviado el reporte, igual que antes con
     * finishAffinity().
     */
    private fun irAConfirmacion() {
        findNavController().navigate(R.id.action_formulario_a_confirmacion)
    }

    /**
     * El usuario pudo prender el GPS o dar el permiso en Ajustes y volver: si ya
     * hay permiso y todavía no tenemos ubicación, reintentamos. No relanza el
     * diálogo del sistema (eso solo pasa desde asegurarUbicacion en la 1ª vez).
     */
    override fun onResume() {
        super.onResume()
        if (!ubicacionLista && tienePermisoUbicacion()) obtenerUbicacion()
    }

    /** Ver BaseFragment.onBindingDestroy: corre antes de liberar el binding,
     *  reemplaza al onDestroy() que tenía la Activity para no dejar el
     *  LocationListener colgado. */
    override fun onBindingDestroy() {
        detenerActualizaciones()
    }

    companion object {
        private const val CHIMBOTE_LAT = -9.0745
        private const val CHIMBOTE_LNG = -78.5936
        private const val TIMEOUT_UBI_MS = 15_000L // corta la espera del fix fresco
        private const val MAX_EDAD_FIX_MS = 2 * 60 * 1000L // fix cacheado válido: 2 min
    }
}
