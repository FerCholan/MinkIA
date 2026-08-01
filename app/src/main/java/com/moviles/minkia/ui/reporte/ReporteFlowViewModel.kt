package com.moviles.minkia.ui.reporte

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Reporte
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.model.ResultadoRegistro
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.ReporteRepository
import com.moviles.minkia.data.source.DetectorBasuraDataSource

/**
 * Estado compartido del flujo de reporte (mockups C09-C12: captura, análisis,
 * formulario y confirmación; FASE 4 de la migración de 18 Activities a 3 con
 * Navigation Component). Antes cada pantalla era una Activity y el dato del
 * paso anterior viajaba al siguiente por putExtra (foto, tipo, confianza,
 * severidad, área, ticket); con los cuatro pasos convertidos en fragments del
 * mismo grafo (nav_reporte), ese dato vive acá: cada fragment lo lee y lo
 * escribe directamente en vez de leerlo de un Bundle.
 *
 * Se obtiene con navGraphViewModels(R.id.nav_reporte) desde los cuatro
 * fragments (CapturaFragment, AnalisisFragment, FormularioFragment,
 * ConfirmacionFragment): esa función ata el ViewModel al NavBackStackEntry
 * del GRAFO nav_reporte, no al de cada fragment individual, así que las
 * cuatro pantallas comparten la MISMA instancia mientras estén dentro del
 * grafo. Al salir (ver ConfirmacionFragment.cerrarFlujo, que hace
 * findNavController().popBackStack(R.id.nav_reporte, true)) ese
 * NavBackStackEntry se destruye y con él este ViewModel: la próxima vez que
 * el ciudadano reporte algo, el flujo arranca desde cero, sin restos de la
 * vuelta anterior.
 */
class ReporteFlowViewModel(
    private val repository: ReporteRepository
) : BaseViewModel() {

    /**
     * Ruta de la foto tomada en Captura (C09), en cache. Es el único dato que
     * aporta ese paso: Análisis la usa para inferir y Formulario la reusa
     * para el thumbnail y para el guardado final.
     */
    var fotoPath: String? = null
        private set

    fun fijarFoto(rutaFoto: String) {
        fotoPath = rutaFoto
    }

    // --- Análisis (C10): corre la inferencia sobre fotoPath ---

    private val _analisis = MutableLiveData<UiState<ResultadoAnalisis>>()
    val analisis: LiveData<UiState<ResultadoAnalisis>> = _analisis

    /**
     * Último resultado ya resuelto. Formulario lo lee acá de forma síncrona
     * en vez de observar el ciclo Loading/Success/Error de Análisis: para
     * cuando Formulario existe, ese ciclo ya terminó en la pantalla anterior.
     */
    var resultadoAnalisis: ResultadoAnalisis? = null
        private set

    fun analizar() = loadInto(_analisis) {
        repository.analizar(fotoPath.orEmpty()).also { resultadoAnalisis = it }
    }

    // --- Envío del reporte (C11): guarda con los datos que completa el ciudadano ---

    private val _envio = MutableLiveData<UiState<ResultadoRegistro>>()
    val envio: LiveData<UiState<ResultadoRegistro>> = _envio

    /** Reporte ya guardado, con ticket. Confirmación lo lee acá. */
    var reporte: Reporte? = null
        private set

    /**
     * El reporte quedó en la cola local y todavía no llegó al servidor. Confirmación
     * lo lee para avisar que la sincronización está pendiente en vez de afirmar que
     * la alerta ya llegó al equipo de gestión.
     */
    var pendienteDeSincronizar: Boolean = false
        private set

    fun enviar(
        tipo: String,
        severidad: Severidad,
        descripcion: String,
        direccion: String,
        zona: String,
        latitud: Double,
        longitud: Double,
        porcentajeCobertura: Int,
        confianza: Int
    ) = loadInto(_envio) {
        val resultado = repository.guardar(
            tipo = tipo,
            severidad = severidad,
            descripcion = descripcion,
            direccion = direccion,
            zona = zona,
            latitud = latitud,
            longitud = longitud,
            fotoPath = fotoPath,
            porcentajeCobertura = porcentajeCobertura,
            confianza = confianza
        )
        // Solo Enviado y Pendiente son éxitos: en ambos el reporte QUEDÓ guardado
        // (en el servidor o en la cola) y el ticket sirve. Error se relanza para que
        // loadInto lo publique como UiState.Error y el formulario NO navegue a la
        // confirmación: antes este caso mostraba un ticket de un reporte perdido.
        when (resultado) {
            is ResultadoRegistro.Enviado -> {
                reporte = resultado.reporte
                pendienteDeSincronizar = false
            }
            is ResultadoRegistro.Pendiente -> {
                reporte = resultado.reporte
                pendienteDeSincronizar = true
            }
            is ResultadoRegistro.Error -> error(resultado.mensaje)
        }
        resultado
    }

    /**
     * Fábrica anidada (mismo patrón que el resto del proyecto: ver
     * PerfilViewModel.Factory, RegistroViewModel.Factory, etc., y las que
     * este ViewModel reemplaza: AnalisisViewModel.Factory y
     * formulario/ReporteViewModel.Factory). Recibe un [Context] en vez del
     * repositorio ya armado porque necesita construir el MISMO
     * [DetectorBasuraDataSource] que antes armaba AnalisisActivity
     * (inferencia YOLOv8/TFLite real, no el mock): esa fuente necesita
     * contexto para mapear el .tflite de assets a memoria.
     *
     * Los cuatro fragments pasan un Factory equivalente a navGraphViewModels,
     * pero en la práctica solo lo usa el primero que pide el ViewModel
     * (siempre CapturaFragment, por ser el destino inicial del grafo):
     * ViewModelProvider ignora la fábrica que le pasen los siguientes en
     * cuanto ya existe una instancia para ese NavBackStackEntry. Se recibe
     * applicationContext (no el Fragment ni su Activity) porque el
     * repositorio construido acá queda retenido por un ViewModel que vive
     * más que cualquier vista puntual del flujo.
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = ReporteRepository(DetectorBasuraDataSource(context))
            return ReporteFlowViewModel(repository) as T
        }
    }
}
