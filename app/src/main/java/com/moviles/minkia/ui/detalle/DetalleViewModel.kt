package com.moviles.minkia.ui.detalle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.repository.CiudadanoRepository

/**
 * ViewModel del detalle de un reporte (mockup C15). No existía antes de la FASE 3
 * de la migración a Navigation Component: DetalleActivity pintaba directo los
 * once extras que le llegaban por Intent y nunca necesitó pedirle nada a un
 * repositorio. Al matar esos extras (ver DetalleFragment y el argumento "ticket"
 * en nav_ciudadano.xml) la pantalla necesita, por primera vez, buscar su propio
 * reporte; de ahí que este ViewModel se cree recién ahora, siguiendo el mismo
 * patrón (BaseViewModel + UiState + Factory) que ya usan Notificaciones y Mis
 * datos.
 */
class DetalleViewModel(
    ticket: String,
    private val repository: CiudadanoRepository = CiudadanoRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<MiReporte>>()
    val uiState: LiveData<UiState<MiReporte>> = _uiState

    init {
        cargar(ticket)
    }

    private fun cargar(ticket: String) = loadInto(_uiState) {
        repository.obtenerReportePorTicket(ticket)
            ?: error("No se encontró el reporte $ticket")
    }

    /** Factory: el ticket llega por argumento de navegación, no hay forma de inyectarlo sin uno. */
    class Factory(
        private val ticket: String,
        private val repository: CiudadanoRepository = CiudadanoRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetalleViewModel(ticket, repository) as T
        }
    }
}
