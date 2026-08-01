package com.moviles.minkia.ui.reportes

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.CiudadanoRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Historial de reportes del vecino (C13): la lista se carga sola al abrir la pantalla. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportesViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = mockk<CiudadanoRepository>()

    private fun reporte(id: String, estado: EstadoReporte) = MiReporte(
        id = id, ticket = "#MK-$id", direccion = "Av. Pardo 123",
        fechaTexto = "12 mar · 10:30", severidad = Severidad.ALTA, estado = estado
    )

    @Test
    fun `la lista se carga sola al abrir la pantalla`() {
        val reportes = listOf(
            reporte("#MK-1", EstadoReporte.RECIBIDO),
            reporte("#MK-2", EstadoReporte.RESUELTO)
        )
        coEvery { repository.misReportes() } returns reportes

        val vm = ReportesViewModel(repository)

        assertEquals(UiState.Success(reportes), vm.uiState.value)
        coVerify(exactly = 1) { repository.misReportes() }
    }

    @Test
    fun `un vecino sin reportes recibe una lista vacia y no un error`() {
        coEvery { repository.misReportes() } returns emptyList()

        val vm = ReportesViewModel(repository)

        assertEquals(UiState.Success(emptyList<MiReporte>()), vm.uiState.value)
    }

    @Test
    fun `si la carga falla el estado queda en Error con el mensaje real`() {
        coEvery { repository.misReportes() } throws RuntimeException("sin conexión")

        val vm = ReportesViewModel(repository)

        assertEquals(UiState.Error("sin conexión"), vm.uiState.value)
    }

    @Test
    fun `un error sin mensaje igual muestra algo entendible al vecino`() {
        coEvery { repository.misReportes() } throws RuntimeException()

        val vm = ReportesViewModel(repository)

        assertEquals(UiState.Error("Ocurrió un error inesperado. Intenta de nuevo."), vm.uiState.value)
    }

    @Test
    fun `recargar vuelve a consultar la lista`() {
        coEvery { repository.misReportes() } returns emptyList()
        val vm = ReportesViewModel(repository) // 1ª carga en init

        vm.cargar()

        coVerify(exactly = 2) { repository.misReportes() }
    }

    @Test
    fun `la lista conserva resueltos y pendientes porque el filtro lo hace la vista`() {
        val reportes = listOf(
            reporte("#MK-1", EstadoReporte.RECIBIDO),
            reporte("#MK-2", EstadoReporte.RESUELTO),
            reporte("#MK-3", EstadoReporte.ANULADO)
        )
        coEvery { repository.misReportes() } returns reportes

        val estado = ReportesViewModel(repository).uiState.value as UiState.Success
        assertEquals(3, estado.data.size)
    }
}
