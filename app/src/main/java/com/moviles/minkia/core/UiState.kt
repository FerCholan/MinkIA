package com.moviles.minkia.core

/**
 * Estado genérico de una pantalla orientada a datos. Una sola definición para
 * toda la app: cualquier pantalla expone un LiveData<UiState<TipoDeDato>> y la
 * vista reacciona a estos tres casos. Evita repetir un sealed class por pantalla.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val mensaje: String) : UiState<Nothing>
}
