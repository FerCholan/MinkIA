package com.moviles.minkia.data.repository

import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.source.ReporteFirestoreDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El repositorio del admin es sobre todo delegación, y lo que se prueba acá es
 * exactamente eso: que cada acción de moderación llegue a la fuente correcta (un
 * cruce entre "resolver" y "anular" cambiaría el destino de un reporte real), más
 * la única pieza con lógica propia: buscar la alerta por id para la pantalla de
 * Validación.
 */
class AdminRepositoryTest {

    private val fuente = mockk<ReporteFirestoreDataSource>(relaxed = true)
    private val repo = AdminRepository(fuente)

    private fun alerta(id: String) = AlertaAdmin(
        id = id, direccion = "Av. Pardo 123", tiempoTexto = "Hace 4 min",
        agrupados = 1, severidad = Severidad.ALTA, nuevo = true, ticket = "#MK-$id"
    )

    @Test
    fun `busca la alerta por id dentro de la bandeja pendiente`() = runBlocking {
        coEvery { fuente.alertasAdmin() } returns listOf(alerta("a"), alerta("b"))

        assertEquals("#MK-b", repo.obtenerAlertaPorId("b")?.ticket)
    }

    @Test
    fun `un id que ya no esta pendiente devuelve null en vez de romper`() = runBlocking {
        // Caso real: el admin lo resolvió desde otro dispositivo y salió de la bandeja.
        coEvery { fuente.alertasAdmin() } returns listOf(alerta("a"))

        assertNull(repo.obtenerAlertaPorId("b"))
    }

    @Test
    fun `con la bandeja vacia no encuentra nada`() = runBlocking {
        coEvery { fuente.alertasAdmin() } returns emptyList()

        assertNull(repo.obtenerAlertaPorId("a"))
    }

    @Test
    fun `cada accion de moderacion llega a la operacion correcta`() = runBlocking {
        repo.validar("1")
        repo.resolver("2")
        repo.marcarDuplicado("3")
        repo.anular("4")
        repo.actualizarNivel("5", Severidad.MEDIA)

        coVerify(exactly = 1) { fuente.validarReporte("1") }
        coVerify(exactly = 1) { fuente.resolverReporte("2") }
        coVerify(exactly = 1) { fuente.marcarDuplicado("3") }
        coVerify(exactly = 1) { fuente.anularReporte("4") }
        coVerify(exactly = 1) { fuente.actualizarSeveridad("5", Severidad.MEDIA) }
    }

    @Test
    fun `resolver no anula ni anular resuelve`() = runBlocking {
        repo.resolver("1")

        coVerify(exactly = 0) { fuente.anularReporte(any()) }
        coVerify(exactly = 0) { fuente.marcarDuplicado(any()) }
    }

    @Test
    fun `la reporteria pasa el rango y el nivel tal cual`() = runBlocking {
        coEvery { fuente.reportesFiltrados(any(), any(), any()) } returns emptyList()

        repo.reportes(desde = 100L, hasta = 900L, severidad = Severidad.ALTA)

        coVerify(exactly = 1) { fuente.reportesFiltrados(100L, 900L, Severidad.ALTA) }
    }
}
