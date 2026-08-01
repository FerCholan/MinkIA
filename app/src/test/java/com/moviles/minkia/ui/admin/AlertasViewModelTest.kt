package com.moviles.minkia.ui.admin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.AdminRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Bandeja de moderación del admin (A02): la lista se carga al abrir la pantalla. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlertasViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = mockk<AdminRepository>()

    private fun alerta(id: String, nuevo: Boolean = true) = AlertaAdmin(
        id = id, direccion = "Av. Pardo 123", tiempoTexto = "Hace 4 min",
        agrupados = 1, severidad = Severidad.ALTA, nuevo = nuevo
    )

    @Test
    fun `la bandeja se carga sola al abrir la pantalla`() {
        val alertas = listOf(alerta("a"), alerta("b"))
        coEvery { repository.alertas() } returns alertas

        val vm = AlertasViewModel(repository)

        assertEquals(UiState.Success(alertas), vm.uiState.value)
        coVerify(exactly = 1) { repository.alertas() }
    }

    @Test
    fun `una bandeja vacia es un exito con lista vacia y no un error`() {
        coEvery { repository.alertas() } returns emptyList()

        val vm = AlertasViewModel(repository)

        assertEquals(UiState.Success(emptyList<AlertaAdmin>()), vm.uiState.value)
    }

    @Test
    fun `si la bandeja falla el admin ve el error y no una lista vieja`() {
        coEvery { repository.alertas() } throws RuntimeException("sin permisos")

        val vm = AlertasViewModel(repository)

        assertEquals(UiState.Error("sin permisos"), vm.uiState.value)
    }

    @Test
    fun `recargar la bandeja vuelve a consultar`() {
        coEvery { repository.alertas() } returns emptyList()
        val vm = AlertasViewModel(repository)

        vm.cargar()

        coVerify(exactly = 2) { repository.alertas() }
    }

    @Test
    fun `el ViewModel respeta el orden que trae el repositorio`() {
        coEvery { repository.alertas() } returns listOf(alerta("nuevo"), alerta("viejo", nuevo = false))

        val estado = AlertasViewModel(repository).uiState.value as UiState.Success
        assertEquals(listOf("nuevo", "viejo"), estado.data.map { it.id })
    }
}
