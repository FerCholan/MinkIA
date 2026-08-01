package com.moviles.minkia.ui.reporte

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.Reporte
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.ReporteRepository
import com.moviles.minkia.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * El estado COMPARTIDO del flujo de reporte (C09 captura -> C10 análisis -> C11
 * formulario -> C12 confirmación). Los cuatro pasos son fragments del mismo grafo
 * y este ViewModel es lo único que los conecta: si la foto o el resultado no
 * viajan bien entre pasos, el vecino termina reportando otra cosa, o nada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReporteFlowViewModelTest {

    @get:Rule
    val instantTask = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = mockk<ReporteRepository>()
    private fun viewModel() = ReporteFlowViewModel(repository)

    private val analisis = ResultadoAnalisis(
        tipo = "Basura acumulada", confianza = 86, severidad = Severidad.ALTA, areaM2 = 4.5
    )

    private val guardado = Reporte(
        ticket = "#MK-2001", tipo = "Basura acumulada", severidad = Severidad.ALTA,
        descripcion = "Montículo", direccion = "Av. Pardo 123",
        latitud = -9.07, longitud = -78.59, fotoPath = "/cache/foto.jpg"
    )

    // ---------- paso 1: la foto ----------

    @Test
    fun `el flujo arranca sin foto`() {
        assertNull(viewModel().fotoPath)
    }

    @Test
    fun `la foto tomada en Captura queda disponible para los pasos siguientes`() {
        val vm = viewModel()
        vm.fijarFoto("/cache/foto.jpg")

        assertEquals("/cache/foto.jpg", vm.fotoPath)
    }

    // ---------- paso 2: el análisis ----------

    @Test
    fun `analizar corre la inferencia sobre la foto que se acaba de tomar`() = runBlocking {
        coEvery { repository.analizar("/cache/foto.jpg") } returns analisis
        val vm = viewModel()
        vm.fijarFoto("/cache/foto.jpg")

        vm.analizar()

        assertEquals(UiState.Success(analisis), vm.analisis.value)
        coVerify(exactly = 1) { repository.analizar("/cache/foto.jpg") }
    }

    @Test
    fun `el resultado del analisis queda cacheado para que Formulario lo lea directo`() {
        coEvery { repository.analizar(any()) } returns analisis
        val vm = viewModel()
        vm.fijarFoto("/cache/foto.jpg")

        assertNull(vm.resultadoAnalisis) // todavía no corrió
        vm.analizar()

        // Formulario lo lee de forma síncrona, sin observar el ciclo Loading/Success.
        assertEquals(analisis, vm.resultadoAnalisis)
    }

    @Test
    fun `si el analisis falla el estado queda en Error y no hay resultado cacheado`() {
        coEvery { repository.analizar(any()) } throws RuntimeException("modelo no disponible")
        val vm = viewModel()
        vm.fijarFoto("/cache/foto.jpg")

        vm.analizar()

        assertEquals(UiState.Error("modelo no disponible"), vm.analisis.value)
        assertNull(vm.resultadoAnalisis)
    }

    @Test
    fun `analizar sin foto no explota manda cadena vacia`() = runBlocking {
        coEvery { repository.analizar("") } returns analisis

        viewModel().analizar()

        coVerify(exactly = 1) { repository.analizar("") }
    }

    // ---------- paso 3: el envío ----------

    @Test
    fun `enviar guarda con la foto del flujo y con los datos del formulario`() = runBlocking {
        coEvery { repository.guardar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns guardado
        val vm = viewModel()
        vm.fijarFoto("/cache/foto.jpg")

        vm.enviar(
            tipo = "Basura acumulada", severidad = Severidad.ALTA, descripcion = "Montículo",
            direccion = "Av. Pardo 123", zona = "Casco Urbano",
            latitud = -9.07, longitud = -78.59, areaM2 = 4.5, confianza = 86
        )

        assertEquals(UiState.Success(guardado), vm.envio.value)
        coVerify(exactly = 1) {
            repository.guardar(
                tipo = "Basura acumulada", severidad = Severidad.ALTA, descripcion = "Montículo",
                direccion = "Av. Pardo 123", zona = "Casco Urbano",
                latitud = -9.07, longitud = -78.59, fotoPath = "/cache/foto.jpg",
                areaM2 = 4.5, confianza = 86
            )
        }
    }

    @Test
    fun `el reporte guardado queda cacheado para la pantalla de Confirmacion`() {
        coEvery { repository.guardar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns guardado
        val vm = viewModel()

        assertNull(vm.reporte)
        vm.enviar("Basura", Severidad.ALTA, "", "", "", 0.0, 0.0, 0.0, 0)

        assertEquals(guardado, vm.reporte)
        assertEquals("#MK-2001", vm.reporte?.ticket)
    }

    @Test
    fun `si el guardado falla Confirmacion no recibe un reporte a medias`() {
        coEvery {
            repository.guardar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("No hay sesión activa")
        val vm = viewModel()

        vm.enviar("Basura", Severidad.ALTA, "", "", "", 0.0, 0.0, 0.0, 0)

        assertEquals(UiState.Error("No hay sesión activa"), vm.envio.value)
        assertNull(vm.reporte)
    }

    @Test
    fun `analisis y envio no se pisan entre si`() {
        coEvery { repository.analizar(any()) } returns analisis
        coEvery { repository.guardar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns guardado
        val vm = viewModel()

        vm.analizar()
        assertNull(vm.envio.value) // analizar no toca el estado del envío

        vm.enviar("Basura", Severidad.ALTA, "", "", "", 0.0, 0.0, 0.0, 0)
        assertEquals(UiState.Success(analisis), vm.analisis.value) // ni el envío el del análisis
    }
}
