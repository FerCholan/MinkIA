package com.moviles.minkia.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.ZonaAfectada
import com.moviles.minkia.data.repository.AdminRepository
import com.moviles.minkia.data.repository.CiudadanoRepository

/**
 * ViewModel de puntos críticos del administrador (A03). Pide dos datos
 * independientes a Firestore: los focos activos (para los marcadores) y las
 * zonas agrupadas (para la lista). Cada uno llega de un repositorio distinto y
 * en un momento distinto, por eso expone un [UiState] separado para cada uno en
 * vez de combinarlos en una sola carga.
 */
class PuntosCriticosViewModel(
    private val repository: CiudadanoRepository = CiudadanoRepository(),
    private val adminRepository: AdminRepository = AdminRepository()
) : BaseViewModel() {

    private val _focosState = MutableLiveData<UiState<List<FocoMapa>>>()
    val focosState: LiveData<UiState<List<FocoMapa>>> = _focosState

    private val _zonasState = MutableLiveData<UiState<List<ZonaAfectada>>>()
    val zonasState: LiveData<UiState<List<ZonaAfectada>>> = _zonasState

    init {
        cargarFocos()
        cargarZonas()
    }

    fun cargarFocos() = loadInto(_focosState) { repository.focosMapa() }

    /** Zonas agrupadas (mismas que el Panel), reales desde Firestore. */
    fun cargarZonas() = loadInto(_zonasState) { adminRepository.zonas() }

    /** Factory para inyectar los repositorios sin librerías de DI. */
    class Factory(
        private val repository: CiudadanoRepository = CiudadanoRepository(),
        private val adminRepository: AdminRepository = AdminRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PuntosCriticosViewModel(repository, adminRepository) as T
        }
    }
}
