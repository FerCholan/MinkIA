package com.moviles.minkia.ui.admin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.FilaReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.AdminRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifica la reportería municipal (A06): que generar() pida al repositorio los
 * reportes del rango/nivel y los emita como Success, y que csv() delegue el armado
 * del CSV en el repositorio (fuente única de formato).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReporteriaViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val filas = listOf(
        FilaReporte(
            id = "doc-2048",
            ticket = "#MK-2048",
            direccion = "Jr. Elías Aguirre 450, Chimbote",
            tipo = "Basura",
            severidad = Severidad.ALTA,
            estado = EstadoReporte.RECIBIDO,
            fechaHoraTexto = "05 jul · 09:41",
            latitud = -9.0745,
            longitud = -78.5936
        )
    )

    @Test
    fun `generar pide los reportes del rango y los emite como Success`() {
        val repository = mockk<AdminRepository>()
        coEvery { repository.reportes(1_000L, 2_000L, Severidad.ALTA) } returns filas

        val viewModel = ReporteriaViewModel(repository)
        viewModel.generar(1_000L, 2_000L, Severidad.ALTA)

        assertEquals(UiState.Success(filas), viewModel.uiState.value)
    }

    @Test
    fun `csv delega el armado en el repositorio`() {
        val repository = mockk<AdminRepository>()
        every { repository.csv(filas) } returns "Ticket,Direccion\n\"#MK-2048\",\"...\"\n"

        val viewModel = ReporteriaViewModel(repository)

        assertEquals("Ticket,Direccion\n\"#MK-2048\",\"...\"\n", viewModel.csv(filas))
    }
}
