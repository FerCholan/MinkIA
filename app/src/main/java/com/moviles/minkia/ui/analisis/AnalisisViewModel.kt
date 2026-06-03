package com.moviles.minkia.ui.analisis

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.repository.ReporteRepository

/**
 * ViewModel del análisis con IA (mockup C10). Dispara la inferencia sobre la
 * foto y publica el ciclo Loading/Success/Error. La Activity solo observa y
 * pinta el progreso y el resultado.
 */
class AnalisisViewModel(
    private val repository: ReporteRepository
) : BaseViewModel() {

    private val _estado = MutableLiveData<UiState<ResultadoAnalisis>>()
    val estado: LiveData<UiState<ResultadoAnalisis>> = _estado

    fun analizar(rutaFoto: String) = loadInto(_estado) { repository.analizar(rutaFoto) }

    class Factory(
        private val repository: ReporteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalisisViewModel(repository) as T
        }
    }
}
