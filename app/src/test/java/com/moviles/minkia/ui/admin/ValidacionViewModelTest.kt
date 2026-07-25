package com.moviles.minkia.ui.admin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.AdminRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Verifica la moderación de un reporte (A04): cada acción termina en Success con
 * el [AccionModeracion] correspondiente sobre el mismo [ValidacionViewModel.uiState],
 * mientras que cambiar el nivel usa [ValidacionViewModel.nivelState], un LiveData
 * aparte que no interfiere con el de las acciones terminales.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ValidacionViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `resolver marca el foco como limpiado y emite RESOLVER`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.resolver("1") } returns Unit

        val viewModel = ValidacionViewModel(repository)
        viewModel.resolver("1")

        assertEquals(UiState.Success(AccionModeracion.RESOLVER), viewModel.uiState.value)
        coVerify(exactly = 1) { repository.resolver("1") }
    }

    @Test
    fun `validar pasa el reporte a EN_PROCESO y emite VALIDAR`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.validar("1") } returns Unit

        val viewModel = ValidacionViewModel(repository)
        viewModel.validar("1")

        assertEquals(UiState.Success(AccionModeracion.VALIDAR), viewModel.uiState.value)
        coVerify(exactly = 1) { repository.validar("1") }
    }

    @Test
    fun `marcarDuplicado emite DUPLICADO`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.marcarDuplicado("1") } returns Unit

        val viewModel = ValidacionViewModel(repository)
        viewModel.marcarDuplicado("1")

        assertEquals(UiState.Success(AccionModeracion.DUPLICADO), viewModel.uiState.value)
        coVerify(exactly = 1) { repository.marcarDuplicado("1") }
    }

    @Test
    fun `anular marca el reporte como ANULADO y emite ANULAR`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.anular("1") } returns Unit

        val viewModel = ValidacionViewModel(repository)
        viewModel.anular("1")

        assertEquals(UiState.Success(AccionModeracion.ANULAR), viewModel.uiState.value)
        coVerify(exactly = 1) { repository.anular("1") }
    }

    @Test
    fun `si una accion de moderacion falla uiState termina en Error`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.resolver("1") } throws RuntimeException("sin permisos")

        val viewModel = ValidacionViewModel(repository)
        viewModel.resolver("1")

        assertEquals(UiState.Error("sin permisos"), viewModel.uiState.value)
    }

    @Test
    fun `actualizarNivel publica la severidad nueva en nivelState sin tocar uiState`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.actualizarNivel("1", Severidad.MEDIA) } returns Unit

        val viewModel = ValidacionViewModel(repository)
        viewModel.actualizarNivel("1", Severidad.MEDIA)

        assertEquals(UiState.Success(Severidad.MEDIA), viewModel.nivelState.value)
        assertNull(viewModel.uiState.value)
        coVerify(exactly = 1) { repository.actualizarNivel("1", Severidad.MEDIA) }
    }

    @Test
    fun `si actualizarNivel falla nivelState termina en Error sin tocar uiState`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.actualizarNivel("1", Severidad.ALTA) } throws RuntimeException("nivel inválido")

        val viewModel = ValidacionViewModel(repository)
        viewModel.actualizarNivel("1", Severidad.ALTA)

        assertEquals(UiState.Error("nivel inválido"), viewModel.nivelState.value)
        assertNull(viewModel.uiState.value)
    }
}
