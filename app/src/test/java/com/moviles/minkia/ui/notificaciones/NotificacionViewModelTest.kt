package com.moviles.minkia.ui.notificaciones

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Notificacion
import com.moviles.minkia.data.model.TipoNotificacion
import com.moviles.minkia.data.repository.CiudadanoRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Notificaciones del vecino (C16): se cargan al abrir y la vista las agrupa por sección. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificacionViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = mockk<CiudadanoRepository>()

    private fun notificacion(grupo: String) =
        Notificacion(TipoNotificacion.RESUELTO, "¡Resuelto!", "Hace 4 min", grupo)

    @Test
    fun `las notificaciones se cargan solas al abrir la pantalla`() {
        val lista = listOf(notificacion("HOY"), notificacion("ESTA SEMANA"))
        coEvery { repository.notificaciones() } returns lista

        val vm = NotificacionViewModel(repository)

        assertEquals(UiState.Success(lista), vm.uiState.value)
        coVerify(exactly = 1) { repository.notificaciones() }
    }

    @Test
    fun `sin notificaciones el estado es una lista vacia y no un error`() {
        coEvery { repository.notificaciones() } returns emptyList()

        val vm = NotificacionViewModel(repository)

        assertEquals(UiState.Success(emptyList<Notificacion>()), vm.uiState.value)
    }

    @Test
    fun `si falla la carga el estado queda en Error`() {
        coEvery { repository.notificaciones() } throws RuntimeException("sin conexión")

        val vm = NotificacionViewModel(repository)

        assertEquals(UiState.Error("sin conexión"), vm.uiState.value)
    }

    @Test
    fun `recargar vuelve a consultar`() {
        coEvery { repository.notificaciones() } returns emptyList()
        val vm = NotificacionViewModel(repository)

        vm.cargar()

        coVerify(exactly = 2) { repository.notificaciones() }
    }

    @Test
    fun `el ViewModel conserva el orden con el que llegan las notificaciones`() {
        val lista = listOf(notificacion("HOY"), notificacion("HOY"), notificacion("ESTA SEMANA"))
        coEvery { repository.notificaciones() } returns lista

        val estado = NotificacionViewModel(repository).uiState.value as UiState.Success
        assertEquals(listOf("HOY", "HOY", "ESTA SEMANA"), estado.data.map { it.grupo })
    }
}
