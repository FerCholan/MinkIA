package com.moviles.minkia.ui.formulario

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Reporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.ReporteRepository

/**
 * ViewModel del formulario de reporte (mockup C11). Guarda el reporte vía el
 * repositorio (offline-first) y publica el ciclo Loading/Success/Error. La
 * Activity solo arma el reporte con lo que ingresó el usuario y observa.
 */
class ReporteViewModel(
    private val repository: ReporteRepository = ReporteRepository()
) : BaseViewModel() {

    private val _estado = MutableLiveData<UiState<Reporte>>()
    val estado: LiveData<UiState<Reporte>> = _estado

    fun enviar(
        tipo: String,
        severidad: Severidad,
        descripcion: String,
        direccion: String,
        latitud: Double,
        longitud: Double,
        fotoPath: String?
    ) = loadInto(_estado) {
        repository.guardar(tipo, severidad, descripcion, direccion, latitud, longitud, fotoPath)
    }

    class Factory(
        private val repository: ReporteRepository = ReporteRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReporteViewModel(repository) as T
        }
    }
}
