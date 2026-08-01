package com.moviles.minkia.data.local

import android.content.Context
import com.moviles.minkia.data.model.ReportePendiente
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * La cola local de reportes pendientes: lo único que separa a un vecino sin
 * internet de perder su reporte. Se prueba contra un filesDir REAL (carpeta
 * temporal), porque el punto es justamente que sobreviva al disco: el JSON, la
 * copia de la foto y el contador que ve la pantalla de Configuración.
 */
class ColaReportesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setup() = runBlocking {
        context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.filesDir } returns tmp.root
        ColaReportes.init(context)
        // init() manda la lectura del disco a segundo plano para no frenar el
        // arranque de la app; acá se espera a propósito para arrancar cada test
        // con el contador ya sincronizado.
        ColaReportes.recargarContador()
    }

    private fun pendiente(
        id: String = "p1",
        fotoPath: String? = null,
        ticket: String = "#MK-2001"
    ) = ReportePendiente(
        id = id,
        userId = "u1",
        ticket = ticket,
        tipo = "Basura acumulada",
        severidad = "ALTA",
        descripcion = "Montículo en la esquina",
        direccion = "Av. Pardo 123, Chimbote",
        zona = "Casco Urbano",
        latitud = -9.07,
        longitud = -78.59,
        porcentajeCobertura = 4,
        confianza = 86,
        fotoPath = fotoPath,
        creadoEn = 1_700_000_000_000L,
        autor = "Fernando",
        autorNivel = 3
    )

    // ---------- encolar / listar / remover ----------

    @Test
    fun `una cola nueva arranca vacia y en cero`() = runBlocking {
        assertTrue(ColaReportes.listar().isEmpty())
        assertEquals(0, ColaReportes.cantidad.value)
    }

    @Test
    fun `encolar persiste el reporte y actualiza el contador que ve la UI`() = runBlocking {
        ColaReportes.encolar(pendiente())

        assertEquals(1, ColaReportes.listar().size)
        assertEquals(1, ColaReportes.cantidad.value)
    }

    @Test
    fun `el reporte vuelve de disco con TODOS sus campos intactos`() = runBlocking {
        val original = pendiente()
        ColaReportes.encolar(original)

        // Sin foto, el round-trip debe ser exacto: si un campo se pierde en el JSON,
        // el reporte llega mutilado al servidor cuando vuelva la conexión.
        assertEquals(original, ColaReportes.listar().single())
    }

    @Test
    fun `los decimales sobreviven al JSON sin redondearse`() = runBlocking {
        ColaReportes.encolar(pendiente())

        val p = ColaReportes.listar().single()
        assertEquals(-9.07, p.latitud, 1e-9)
        assertEquals(-78.59, p.longitud, 1e-9)
        assertEquals(4, p.porcentajeCobertura)
    }

    @Test
    fun `la cola conserva el orden de llegada`() = runBlocking {
        ColaReportes.encolar(pendiente(id = "a"))
        ColaReportes.encolar(pendiente(id = "b"))
        ColaReportes.encolar(pendiente(id = "c"))

        assertEquals(listOf("a", "b", "c"), ColaReportes.listar().map { it.id })
    }

    @Test
    fun `remover saca solo ese reporte y baja el contador`() = runBlocking {
        ColaReportes.encolar(pendiente(id = "a"))
        ColaReportes.encolar(pendiente(id = "b"))

        ColaReportes.remover("a")

        assertEquals(listOf("b"), ColaReportes.listar().map { it.id })
        assertEquals(1, ColaReportes.cantidad.value)
    }

    @Test
    fun `remover un id que no existe no rompe ni altera la cola`() = runBlocking {
        ColaReportes.encolar(pendiente(id = "a"))

        ColaReportes.remover("fantasma")

        assertEquals(1, ColaReportes.listar().size)
        assertEquals(1, ColaReportes.cantidad.value)
    }

    // ---------- la foto ----------

    @Test
    fun `la foto se copia a un lugar persistente y sobrevive al borrado del cache`() = runBlocking {
        val cache = File(tmp.root, "cache").apply { mkdirs() }
        val original = File(cache, "foto.jpg").apply { writeText("bytes-de-la-foto") }

        ColaReportes.encolar(pendiente(id = "p1", fotoPath = original.absolutePath))
        original.delete() // el sistema limpia cacheDir

        val guardada = File(ColaReportes.listar().single().fotoPath!!)
        assertTrue("la foto encolada debe existir fuera del cache", guardada.exists())
        assertEquals("bytes-de-la-foto", guardada.readText())
        assertFalse(guardada.absolutePath == original.absolutePath)
    }

    @Test
    fun `remover borra tambien la foto local para no dejar basura en el telefono`() = runBlocking {
        val cache = File(tmp.root, "cache").apply { mkdirs() }
        val original = File(cache, "foto.jpg").apply { writeText("x") }
        ColaReportes.encolar(pendiente(id = "p1", fotoPath = original.absolutePath))
        val copia = File(ColaReportes.listar().single().fotoPath!!)

        ColaReportes.remover("p1")

        assertFalse("la foto copiada debe borrarse al salir de la cola", copia.exists())
    }

    @Test
    fun `un reporte sin foto queda con fotoPath nulo y no con el texto null`() = runBlocking {
        ColaReportes.encolar(pendiente(fotoPath = null))

        assertNull(ColaReportes.listar().single().fotoPath)
    }

    @Test
    fun `si la foto ya no existe el reporte se encola igual sin foto`() = runBlocking {
        ColaReportes.encolar(pendiente(fotoPath = "/ruta/que/no/existe.jpg"))

        assertEquals(1, ColaReportes.listar().size)
        assertNull(ColaReportes.listar().single().fotoPath)
    }

    // ---------- persistencia entre arranques ----------

    @Test
    fun `al reabrir la app el contador refleja lo que quedo en disco`() = runBlocking {
        ColaReportes.encolar(pendiente(id = "a"))
        ColaReportes.encolar(pendiente(id = "b"))

        ColaReportes.init(context) // simula el reinicio de la app
        ColaReportes.recargarContador()

        assertEquals(2, ColaReportes.cantidad.value)
        assertEquals(listOf("a", "b"), ColaReportes.listar().map { it.id })
    }

    @Test
    fun `un archivo de cola corrupto no cuelga la app arranca vacia`() = runBlocking {
        File(tmp.root, "reportes_pendientes.json").writeText("{esto no es un array json")

        ColaReportes.init(context)
        ColaReportes.recargarContador()

        assertEquals(0, ColaReportes.cantidad.value)
        assertTrue(ColaReportes.listar().isEmpty())
    }

    // ---------- durabilidad: la cola no puede evaporarse en silencio ----------

    @Test
    fun `una escritura cortada a la mitad se recupera del respaldo`() = runBlocking {
        // Escenario real: el proceso muere mientras se vuelca el JSON y el archivo
        // principal queda truncado. Antes eso devolvía una lista vacía y el vecino
        // perdía TODOS sus pendientes sin enterarse.
        ColaReportes.encolar(pendiente(id = "a"))
        ColaReportes.encolar(pendiente(id = "b")) // esta escritura deja el respaldo con "a"

        File(tmp.root, "reportes_pendientes.json").writeText("[{\"id\":\"a\",\"userId\"")

        ColaReportes.recargarContador()

        val recuperados = ColaReportes.listar()
        assertFalse("la cola no debe quedar vacía si hay respaldo", recuperados.isEmpty())
        assertFalse(ColaReportes.colaDaniada.value)
    }

    @Test
    fun `una cola ilegible sin respaldo se marca como daniada y conserva la evidencia`() = runBlocking {
        File(tmp.root, "reportes_pendientes.json").writeText("{esto no es un array json")

        ColaReportes.recargarContador()

        assertTrue("se debe avisar el daño, no fingir que no había nada", ColaReportes.colaDaniada.value)
        assertTrue(
            "el archivo dañado debe conservarse para poder recuperarlo",
            File(tmp.root, "reportes_pendientes.corrupto").exists()
        )
    }

    @Test
    fun `cada escritura deja respaldo del contenido bueno anterior`() = runBlocking {
        ColaReportes.encolar(pendiente(id = "a"))
        ColaReportes.encolar(pendiente(id = "b"))

        val bak = File(tmp.root, "reportes_pendientes.bak")
        assertTrue("debe existir el respaldo", bak.exists())
        assertTrue("el respaldo guarda el estado previo", bak.readText().contains("\"a\""))
    }

    @Test
    fun `la escritura no deja archivos temporales sueltos`() = runBlocking {
        ColaReportes.encolar(pendiente(id = "a"))

        assertFalse(File(tmp.root, "reportes_pendientes.tmp").exists())
        assertTrue(File(tmp.root, "reportes_pendientes.json").exists())
    }

    @Test
    fun `recargar el contador lo pone al dia con lo que hay en disco`() = runBlocking {
        // Es el camino que usa el arranque: init() agenda esta misma lectura en
        // segundo plano en vez de hacerla en el hilo principal.
        ColaReportes.encolar(pendiente(id = "a"))
        ColaReportes.encolar(pendiente(id = "b"))

        ColaReportes.recargarContador()

        assertEquals(2, ColaReportes.cantidad.value)
    }
}
