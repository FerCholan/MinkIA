package com.moviles.minkia.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.FilaReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.AdminRepository

/**
 * ViewModel del reporte municipal (A06). Pide al repositorio los reportes que
 * caen en el rango de fechas y nivel elegidos. La vista decide los filtros; el
 * ViewModel solo ejecuta la consulta y publica el estado.
 */
class ReporteriaViewModel(
    private val repository: AdminRepository = AdminRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<List<FilaReporte>>>()
    val uiState: LiveData<UiState<List<FilaReporte>>> = _uiState

    fun generar(desde: Long, hasta: Long, severidad: Severidad?) =
        loadInto(_uiState) { repository.reportes(desde, hasta, severidad) }

    /** Delega el armado del CSV en el repositorio (fuente única de formato). */
    fun csv(filas: List<FilaReporte>): String = repository.csv(filas)

    class Factory(
        private val repository: AdminRepository = AdminRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReporteriaViewModel(repository) as T
        }
    }
}
