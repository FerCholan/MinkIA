package com.moviles.minkia.data.repository

import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.FocoMapa
import com.moviles.minkia.data.model.MiReporte
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
 * La búsqueda de un reporte es la que sostiene la pantalla de Detalle (C15) desde
 * DOS entradas distintas: "Mis reportes" y el Mapa. Compone dos consultas que ya
 * existen, con un orden que importa: primero los míos (cubre cualquier estado,
 * incluidos los resueltos), y recién si no está, los focos pendientes de toda la
 * comunidad.
 *
 * Busca por ID de documento, no por ticket. Ese es el arreglo del bug más caro que
 * tenía la app: el ticket se generaba desde el reloj con solo 8000 valores
 * posibles, así que dos reportes distintos podían compartirlo, y como esta
 * búsqueda mira PRIMERO los reportes propios, una colisión hacía que tocar el foco
 * de otro vecino en el mapa abriera un reporte tuyo.
 */
class CiudadanoRepositoryTest {

    private val fuente = mockk<ReporteFirestoreDataSource>()
    private val repo = CiudadanoRepository(fuente)

    private fun miReporte(
        id: String,
        ticket: String = "#MK-AAAA1111",
        estado: EstadoReporte = EstadoReporte.RECIBIDO
    ) = MiReporte(
        id = id, ticket = ticket, direccion = "Av. Pardo 123", fechaTexto = "12 mar · 10:30",
        severidad = Severidad.ALTA, estado = estado, areaM2 = 4.5, vecinos = 7,
        fotoUrl = "https://foto", tipo = "Basura", confianza = 86,
        autor = "Fernando", autorNivel = 3
    )

    private fun foco(id: String, ticket: String = "#MK-BBBB2222") = FocoMapa(
        id = id, latitud = -9.07, longitud = -78.59, severidad = Severidad.MEDIA,
        direccion = "Jr. Bolognesi 456", tipo = "Escombros", ticket = ticket,
        estado = EstadoReporte.EN_PROCESO, fotoUrl = "https://otra", zona = "Bellamar",
        confianza = 70, areaM2 = 2.0, autor = "Vecina", autorNivel = 2
    )

    @Test
    fun `encuentra el reporte propio sin llegar a consultar el mapa`() = runBlocking {
        coEvery { fuente.misReportes() } returns listOf(miReporte("doc-1"))

        assertEquals("doc-1", repo.obtenerReportePorId("doc-1")?.id)
        coVerify(exactly = 0) { fuente.obtenerFocosMapa() } // no gasta una consulta de más
    }

    @Test
    fun `encuentra el reporte propio aunque ya este resuelto`() = runBlocking {
        // El mapa solo trae pendientes: si esto fallara, un reporte resuelto propio
        // no se podría abrir desde "Mis reportes".
        coEvery { fuente.misReportes() } returns listOf(miReporte("doc-1", estado = EstadoReporte.RESUELTO))

        assertEquals(EstadoReporte.RESUELTO, repo.obtenerReportePorId("doc-1")?.estado)
    }

    @Test
    fun `si el reporte es de otro vecino lo busca entre los focos del mapa`() = runBlocking {
        coEvery { fuente.misReportes() } returns emptyList()
        coEvery { fuente.obtenerFocosMapa() } returns listOf(foco("doc-9"))

        val r = repo.obtenerReportePorId("doc-9")
        assertEquals("doc-9", r?.id)
        assertEquals("Jr. Bolognesi 456", r?.direccion)
        assertEquals(EstadoReporte.EN_PROCESO, r?.estado)
        assertEquals("Escombros", r?.tipo)
        assertEquals(2, r?.autorNivel)
    }

    @Test
    fun `el reporte de otro vecino no inventa datos que el mapa no tiene`() = runBlocking {
        coEvery { fuente.misReportes() } returns emptyList()
        coEvery { fuente.obtenerFocosMapa() } returns listOf(foco("doc-9"))

        val r = repo.obtenerReportePorId("doc-9")!!
        assertEquals(0, r.vecinos)      // el mapa nunca trajo ese dato
        assertEquals("", r.fechaTexto)  // ni la fecha: mejor vacío que inventado
    }

    @Test
    fun `un id que no existe en ningun lado devuelve null`() = runBlocking {
        coEvery { fuente.misReportes() } returns listOf(miReporte("doc-1"))
        coEvery { fuente.obtenerFocosMapa() } returns listOf(foco("doc-2"))

        assertNull(repo.obtenerReportePorId("doc-inexistente"))
    }

    @Test
    fun `dos reportes con el MISMO ticket ya no se confunden entre si`() = runBlocking {
        // Este es exactamente el escenario que antes rompía: un reporte propio y un
        // foco ajeno compartiendo ticket. Tocar el foco del mapa abría el propio.
        val ticketRepetido = "#MK-3F7A2B91"
        coEvery { fuente.misReportes() } returns listOf(miReporte("mio", ticket = ticketRepetido))
        coEvery { fuente.obtenerFocosMapa() } returns listOf(foco("ajeno", ticket = ticketRepetido))

        assertEquals("Av. Pardo 123", repo.obtenerReportePorId("mio")?.direccion)
        assertEquals("Jr. Bolognesi 456", repo.obtenerReportePorId("ajeno")?.direccion)
    }
}
