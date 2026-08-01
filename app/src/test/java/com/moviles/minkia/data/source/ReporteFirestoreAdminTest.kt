package com.moviles.minkia.data.source

import com.google.firebase.Timestamp
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.FilaReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.util.authFake
import com.moviles.minkia.util.documentoFake
import com.moviles.minkia.util.firestoreCon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Lógica del PANEL ADMINISTRADOR sobre la misma colección `reportes`: la bandeja
 * de moderación (A02), las zonas afectadas (A03), el reporte municipal filtrado
 * (A06) y el CSV que se exporta. Nada de esto tenía prueba.
 */
class ReporteFirestoreAdminTest {

    private fun ts(ms: Long) = Timestamp(Date(ms))

    private fun reporte(
        id: String = "d1",
        estado: String = EstadoReporte.RECIBIDO.name,
        severidad: String = Severidad.MEDIA.name,
        creadoEn: Long? = 1_000L,
        vararg extra: Pair<String, Any?>
    ) = documentoFake(
        id,
        "estado" to estado,
        "severidad" to severidad,
        "creadoEn" to creadoEn?.let { ts(it) },
        *extra
    )

    private fun fuente(docs: List<com.google.firebase.firestore.DocumentSnapshot>) =
        ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

    // ---------- bandeja de moderación (A02) ----------

    @Test
    fun `la bandeja solo muestra reportes accionables`() = runBlocking {
        val docs = listOf(
            reporte(id = "recibido", estado = EstadoReporte.RECIBIDO.name),
            reporte(id = "enProceso", estado = EstadoReporte.EN_PROCESO.name),
            reporte(id = "resuelto", estado = EstadoReporte.RESUELTO.name),
            reporte(id = "duplicado", estado = EstadoReporte.DUPLICADO.name),
            reporte(id = "anulado", estado = EstadoReporte.ANULADO.name)
        )

        val ids = fuente(docs).alertasAdmin().map { it.id }
        assertEquals(setOf("recibido", "enProceso"), ids.toSet())
    }

    @Test
    fun `la bandeja ordena del mas nuevo al mas viejo y marca como nuevo solo lo RECIBIDO`() = runBlocking {
        val docs = listOf(
            reporte(id = "viejo", creadoEn = 1_000L, estado = EstadoReporte.EN_PROCESO.name),
            reporte(id = "nuevo", creadoEn = 9_000L, estado = EstadoReporte.RECIBIDO.name)
        )

        val alertas = fuente(docs).alertasAdmin()
        assertEquals(listOf("nuevo", "viejo"), alertas.map { it.id })
        assertTrue(alertas.first().nuevo)   // RECIBIDO = todavía nadie lo miró
        assertFalse(alertas.last().nuevo)   // EN_PROCESO = ya validado
    }

    @Test
    fun `un reporte sin direccion muestra Chimbote en vez de un renglon vacio`() = runBlocking {
        val alerta = fuente(listOf(reporte())).alertasAdmin().single()
        assertEquals("Chimbote, Áncash", alerta.direccion)
    }

    @Test
    fun `el tiempo relativo de la bandeja se lee en minutos horas o dias`() = runBlocking {
        val ahora = System.currentTimeMillis()
        val docs = listOf(
            reporte(id = "a", creadoEn = ahora - 30_000),                     // 30 s
            reporte(id = "b", creadoEn = ahora - 10 * 60_000L),               // 10 min
            reporte(id = "c", creadoEn = ahora - 3 * 60 * 60_000L),           // 3 h
            reporte(id = "d", creadoEn = ahora - 2 * 24 * 60 * 60_000L),      // 2 d
            reporte(id = "e", creadoEn = null)                                // sin fecha
        )

        val textos = fuente(docs).alertasAdmin().associate { it.id to it.tiempoTexto }
        assertEquals("Recién", textos["a"])
        assertEquals("Hace 10 min", textos["b"])
        assertEquals("Hace 3 h", textos["c"])
        assertEquals("Hace 2 d", textos["d"])
        assertEquals("—", textos["e"])
    }

    @Test
    fun `el filtro de estados que se manda al servidor coincide con el enum`() {
        // Los estados accionables se mandan como texto en un whereIn, porque es lo
        // que está guardado en el documento. Si alguien agrega un estado pendiente
        // nuevo al enum y se olvida de esta lista, la bandeja del admin dejaría de
        // mostrar reportes accionables SIN que falle nada más.
        val segunElEnum = EstadoReporte.entries.filter { it.esPendiente }.map { it.name }

        assertEquals(segunElEnum.toSet(), ReporteFirestoreDataSource.ESTADOS_PENDIENTES.toSet())
    }

    @Test
    fun `cada fila del reporte municipal viaja con el id de su documento`() = runBlocking {
        // El id es lo que identifica la fila en la lista (DiffUtil): con el ticket,
        // dos reportes repetidos se confundían como el mismo ítem.
        val docs = listOf(
            reporte(id = "doc-a", creadoEn = 1_000L, extra = arrayOf("ticket" to "#MK-IGUAL")),
            reporte(id = "doc-b", creadoEn = 2_000L, extra = arrayOf("ticket" to "#MK-IGUAL"))
        )

        val filas = fuente(docs).reportesFiltrados(0, Long.MAX_VALUE, null)
        assertEquals(setOf("doc-a", "doc-b"), filas.map { it.id }.toSet())
    }

    // ---------- zonas afectadas (A03) ----------

    @Test
    fun `las zonas agrupan los focos activos y se ordenan por cantidad`() = runBlocking {
        val docs = listOf(
            reporte(id = "1", extra = arrayOf("zona" to "Bellamar")),
            reporte(id = "2", extra = arrayOf("zona" to "Bellamar")),
            reporte(id = "3", extra = arrayOf("zona" to "Bellamar")),
            reporte(id = "4", extra = arrayOf("zona" to "Miraflores")),
            reporte(id = "5", extra = arrayOf("zona" to "Miraflores")),
            reporte(id = "6", extra = arrayOf("zona" to "Casco Urbano")),
            // Terminal: no debe contar como foco activo de su zona.
            reporte(id = "7", estado = EstadoReporte.RESUELTO.name, extra = arrayOf("zona" to "Bellamar"))
        )

        val zonas = fuente(docs).zonasAfectadas()
        assertEquals(listOf("Bellamar", "Miraflores", "Casco Urbano"), zonas.map { it.nombre })
        assertEquals(listOf(3, 2, 1), zonas.map { it.focos })
    }

    @Test
    fun `la zona se pinta con la peor severidad de sus focos`() = runBlocking {
        val docs = listOf(
            reporte(id = "1", severidad = Severidad.BAJA.name, extra = arrayOf("zona" to "Z")),
            reporte(id = "2", severidad = Severidad.ALTA.name, extra = arrayOf("zona" to "Z")),
            reporte(id = "3", severidad = Severidad.MEDIA.name, extra = arrayOf("zona" to "Z"))
        )

        assertEquals(Severidad.ALTA, fuente(docs).zonasAfectadas().single().severidad)
    }

    @Test
    fun `el centro de la zona es el promedio de las coordenadas de sus focos`() = runBlocking {
        val docs = listOf(
            reporte(id = "1", extra = arrayOf("zona" to "Z", "latitud" to -9.0, "longitud" to -78.0)),
            reporte(id = "2", extra = arrayOf("zona" to "Z", "latitud" to -9.2, "longitud" to -78.4))
        )

        val zona = fuente(docs).zonasAfectadas().single()
        assertEquals(-9.1, zona.latitud, 1e-9)
        assertEquals(-78.2, zona.longitud, 1e-9)
    }

    @Test
    fun `una zona sin ninguna coordenada no rompe el promedio`() = runBlocking {
        val zona = fuente(listOf(reporte(extra = arrayOf("zona" to "Z")))).zonasAfectadas().single()
        assertEquals(0.0, zona.latitud, 1e-9)
        assertEquals(0.0, zona.longitud, 1e-9)
    }

    // ---------- reportería municipal (A06) ----------

    @Test
    fun `el rango de fechas del reporte municipal incluye los extremos`() = runBlocking {
        val docs = listOf(
            reporte(id = "antes", creadoEn = 999L),
            reporte(id = "borde-inicio", creadoEn = 1_000L),
            reporte(id = "medio", creadoEn = 1_500L),
            reporte(id = "borde-fin", creadoEn = 2_000L),
            reporte(id = "despues", creadoEn = 2_001L)
        )

        val ids = fuente(docs).reportesFiltrados(desde = 1_000L, hasta = 2_000L, severidad = null).map { it.ticket }
        // El ticket va vacío en estos documentos: comparamos por tamaño y orden de fecha.
        assertEquals(3, ids.size)
    }

    @Test
    fun `el reporte municipal filtra por nivel cuando se elige uno`() = runBlocking {
        val docs = listOf(
            reporte(id = "1", severidad = Severidad.ALTA.name, extra = arrayOf("ticket" to "#A")),
            reporte(id = "2", severidad = Severidad.BAJA.name, extra = arrayOf("ticket" to "#B")),
            reporte(id = "3", severidad = Severidad.ALTA.name, extra = arrayOf("ticket" to "#C"))
        )

        val ds = fuente(docs)
        assertEquals(
            setOf("#A", "#C"),
            ds.reportesFiltrados(0, Long.MAX_VALUE, Severidad.ALTA).map { it.ticket }.toSet()
        )
        assertEquals(3, ds.reportesFiltrados(0, Long.MAX_VALUE, null).size) // null = todos los niveles
    }

    @Test
    fun `un reporte sin fecha queda fuera del reporte municipal`() = runBlocking {
        val docs = listOf(reporte(id = "sinFecha", creadoEn = null))
        assertTrue(fuente(docs).reportesFiltrados(0, Long.MAX_VALUE, null).isEmpty())
    }

    @Test
    fun `el reporte municipal sale del mas nuevo al mas viejo`() = runBlocking {
        val docs = listOf(
            reporte(id = "1", creadoEn = 1_000L, extra = arrayOf("ticket" to "#viejo")),
            reporte(id = "2", creadoEn = 9_000L, extra = arrayOf("ticket" to "#nuevo"))
        )

        assertEquals(
            listOf("#nuevo", "#viejo"),
            fuente(docs).reportesFiltrados(0, Long.MAX_VALUE, null).map { it.ticket }
        )
    }

    // ---------- CSV municipal ----------

    @Test
    fun `el CSV lleva la cabecera y una linea por reporte`() {
        val csv = fuente(emptyList()).csvDe(
            listOf(
                FilaReporte(
                    "doc-1", "#MK-2001", "Av. Pardo 123", "Basura acumulada",
                    Severidad.ALTA, EstadoReporte.RESUELTO, "12 mar · 10:30", -9.07, -78.59
                )
            )
        )
        val lineas = csv.trim().lines()

        assertEquals("Ticket,Direccion,Tipo,Severidad,Estado,Fecha,Latitud,Longitud", lineas[0])
        assertEquals(2, lineas.size)
        assertTrue(lineas[1].contains("\"#MK-2001\""))
        assertTrue(lineas[1].contains("\"Resuelto\"")) // usa la etiqueta legible, no el name()
    }

    @Test
    fun `una direccion con coma no parte la fila del CSV`() {
        val csv = fuente(emptyList()).csvDe(
            listOf(
                FilaReporte(
                    "doc-1", "#MK-1", "Miraflores Alto, Chimbote, Áncash", "Basura",
                    Severidad.BAJA, EstadoReporte.RECIBIDO, "1 ene · 00:00", 0.0, 0.0
                )
            )
        )

        assertEquals(2, csv.trim().lines().size) // sigue siendo UNA fila de datos
        assertTrue(csv.contains("\"Miraflores Alto, Chimbote, Áncash\""))
    }

    @Test
    fun `las comillas dentro de un campo se duplican como manda el estandar CSV`() {
        val csv = fuente(emptyList()).csvDe(
            listOf(
                FilaReporte(
                    "doc-1", "#MK-1", "Frente al \"mercado\"", "Basura",
                    Severidad.BAJA, EstadoReporte.RECIBIDO, "1 ene · 00:00", 0.0, 0.0
                )
            )
        )

        assertTrue(csv.contains("\"Frente al \"\"mercado\"\"\""))
    }

    @Test
    fun `sin filas el CSV es solo la cabecera`() {
        assertEquals(
            "Ticket,Direccion,Tipo,Severidad,Estado,Fecha,Latitud,Longitud\n",
            fuente(emptyList()).csvDe(emptyList())
        )
    }
}
