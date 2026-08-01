package com.moviles.minkia.ui.detalle

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Detalle de un reporte (C15). Desde la migración a Navigation Component la
 * pantalla solo recibe el TICKET como argumento y tiene que buscar el reporte
 * completo: si no lo encuentra, el vecino no puede quedar con la pantalla en
 * blanco, tiene que ver un error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = mockk<CiudadanoRepository>()

    private val reporte = MiReporte(
        id = "doc-1", ticket = "#MK-3F7A2B91", direccion = "Av. Pardo 123",
        fechaTexto = "12 mar · 10:30", severidad = Severidad.ALTA,
        estado = EstadoReporte.EN_PROCESO, areaM2 = 4.5,
        vecinos = 7, fotoUrl = "https://foto", tipo = "Basura", confianza = 86
    )

    @Test
    fun `carga el reporte del id recibido por navegacion`() {
        coEvery { repository.obtenerReportePorId("doc-1") } returns reporte

        val vm = DetalleViewModel("doc-1", repository)

        assertEquals(UiState.Success(reporte), vm.uiState.value)
        coVerify(exactly = 1) { repository.obtenerReportePorId("doc-1") }
    }

    @Test
    fun `un id inexistente termina en Error y no en una pantalla vacia`() {
        coEvery { repository.obtenerReportePorId("doc-fantasma") } returns null

        val vm = DetalleViewModel("doc-fantasma", repository)

        val estado = vm.uiState.value
        assertTrue(estado is UiState.Error)
        assertTrue((estado as UiState.Error).mensaje.isNotBlank())
    }

    @Test
    fun `si la consulta falla el mensaje del error llega a la pantalla`() {
        coEvery { repository.obtenerReportePorId(any()) } throws RuntimeException("sin conexión")

        val vm = DetalleViewModel("doc-1", repository)

        assertEquals(UiState.Error("sin conexión"), vm.uiState.value)
    }

    @Test
    fun `la Factory arma el ViewModel con el id que le pasan`() {
        coEvery { repository.obtenerReportePorId("doc-1") } returns reporte

        val vm = DetalleViewModel.Factory("doc-1", repository)
            .create(DetalleViewModel::class.java)

        assertEquals(UiState.Success(reporte), vm.uiState.value)
    }
}
