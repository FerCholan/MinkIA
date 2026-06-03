package com.moviles.minkia.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.repository.AdminRepository

/** ViewModel de la bandeja de alertas (A02). */
class AlertasViewModel(
    private val repository: AdminRepository = AdminRepository()
) : BaseViewModel() {

    private val _uiState = MutableLiveData<UiState<List<AlertaAdmin>>>()
    val uiState: LiveData<UiState<List<AlertaAdmin>>> = _uiState

    init {
        cargar()
    }

    fun cargar() = loadInto(_uiState) { repository.alertas() }

    class Factory(
        private val repository: AdminRepository = AdminRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AlertasViewModel(repository) as T
        }
    }
}
