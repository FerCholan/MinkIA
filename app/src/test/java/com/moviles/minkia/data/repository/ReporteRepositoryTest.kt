package com.moviles.minkia.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moviles.minkia.data.local.ColaReportes
import com.moviles.minkia.data.model.ReportePendiente
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.source.AnalisisDataSource
import com.moviles.minkia.data.source.ReporteFirestoreDataSource
import com.moviles.minkia.data.source.ResultadoEnvio
import com.moviles.minkia.data.sync.SincronizadorReportes
import com.moviles.minkia.util.authFake
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El contrato OFFLINE-FIRST del guardado: el reporte del vecino NUNCA se pierde.
 *
 * Es la regla más cara de la app y la que no tenía ninguna prueba. Hay cuatro
 * caminos y cada uno decide algo distinto sobre la cola local:
 *   - sin internet          -> a la cola (se reintenta después)
 *   - enviado               -> NO a la cola (ya está en el servidor)
 *   - fallo transitorio     -> a la cola
 *   - fallo permanente      -> NO a la cola (reintentarlo sería para siempre)
 * Confundir dos de esos caminos significa reportes duplicados o reportes perdidos.
 */
class ReporteRepositoryTest {

    private lateinit var analisis: AnalisisDataSource
    private lateinit var fuente: ReporteFirestoreDataSource
    private lateinit var perfil: PerfilRepository

    @Before
    fun setup() {
        // Firebase no está inicializado en la JVM: hay que interceptar las fábricas
        // ANTES de tocar los objetos singleton, porque su inicialización las llama.
        mockkStatic(FirebaseAuth::class)
        every { FirebaseAuth.getInstance() } returns authFake(uid = "u1")
        mockkStatic(FirebaseFirestore::class)
        every { FirebaseFirestore.getInstance() } returns mockk(relaxed = true)

        mockkObject(SincronizadorReportes)
        mockkObject(ColaReportes)
        coEvery { ColaReportes.encolar(any()) } just Runs

        analisis = mockk()
        fuente = mockk()
        perfil = mockk()
        coEvery { perfil.nombreVisible() } returns "Fernando"
        coEvery { fuente.nivelUsuario() } returns 3
    }

    @After
    fun teardown() = unmockkAll()

    private fun repo() = ReporteRepository(analisis, fuente, perfil)

    private suspend fun guardar() = repo().guardar(
        tipo = "Basura acumulada",
        severidad = Severidad.ALTA,
        descripcion = "Montículo en la esquina",
        direccion = "Av. Pardo 123, Chimbote",
        zona = "Casco Urbano",
        latitud = -9.07,
        longitud = -78.59,
        fotoPath = "/cache/foto.jpg",
        areaM2 = 4.5,
        confianza = 86
    )

    // ---------- los cuatro caminos de la cola ----------

    @Test
    fun `sin internet el reporte va a la cola y ni se intenta enviar`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns false

        guardar()

        coVerify(exactly = 1) { ColaReportes.encolar(any()) }
        coVerify(exactly = 0) { fuente.enviarReporte(any()) }
    }

    @Test
    fun `si el envio sale bien el reporte NO se encola`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { fuente.enviarReporte(any()) } returns ResultadoEnvio.ENVIADO

        guardar()

        coVerify(exactly = 1) { fuente.enviarReporte(any()) }
        coVerify(exactly = 0) { ColaReportes.encolar(any()) }
    }

    @Test
    fun `ante un fallo transitorio el reporte se conserva en la cola`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { fuente.enviarReporte(any()) } returns ResultadoEnvio.REINTENTAR

        guardar()

        coVerify(exactly = 1) { ColaReportes.encolar(any()) }
    }

    @Test
    fun `ante un fallo permanente el reporte no se encola para no reintentar por siempre`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns true
        coEvery { fuente.enviarReporte(any()) } returns ResultadoEnvio.DESCARTAR

        guardar()

        coVerify(exactly = 0) { ColaReportes.encolar(any()) }
    }

    // ---------- lo que el vecino ve y lo que viaja ----------

    @Test
    fun `el vecino recibe su ticket aunque no haya internet`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns false

        val reporte = guardar()

        assertTrue("ticket inesperado: ${reporte.ticket}", reporte.ticket.matches(Regex("#MK-[0-9A-F]{8}")))
        assertEquals("Basura acumulada", reporte.tipo)
        assertEquals(Severidad.ALTA, reporte.severidad)
        assertEquals("/cache/foto.jpg", reporte.fotoPath)
    }

    @Test
    fun `el ticket sale del id del reporte y no del reloj`() = runBlocking {
        // El ticket viejo era 2000 + (currentTimeMillis % 8000): ocho mil valores y
        // un ciclo cada ocho segundos, así que dos reportes seguidos podían caer con
        // el mismo. Ahora se deriva del UUID del documento.
        every { SincronizadorReportes.hayInternet() } returns false
        val encolados = mutableListOf<ReportePendiente>()
        coEvery { ColaReportes.encolar(capture(encolados)) } just Runs

        val tickets = (1..50).map { guardar().ticket }

        assertEquals("50 reportes deben dar 50 tickets distintos", 50, tickets.toSet().size)
        // Y cada ticket corresponde al id de SU reporte, no a otro.
        encolados.forEach { p ->
            assertTrue(
                "ticket ${p.ticket} no deriva del id ${p.id}",
                p.ticket.removePrefix("#MK-").equals(
                    p.id.filter { it.isLetterOrDigit() }.take(8),
                    ignoreCase = true
                )
            )
        }
    }

    @Test
    fun `el pendiente encolado lleva todos los datos del formulario`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns false
        val capturado = slot<ReportePendiente>()
        coEvery { ColaReportes.encolar(capture(capturado)) } just Runs

        val reporte = guardar()
        val p = capturado.captured

        assertEquals(reporte.ticket, p.ticket) // el ticket que ve el vecino es el que viaja
        assertEquals("u1", p.userId)
        assertEquals("ALTA", p.severidad)      // se serializa por name(), no por ordinal
        assertEquals("Casco Urbano", p.zona)
        assertEquals(-9.07, p.latitud, 1e-9)
        assertEquals(-78.59, p.longitud, 1e-9)
        assertEquals(4.5, p.areaM2, 1e-9)
        assertEquals(86, p.confianza)
        assertEquals("/cache/foto.jpg", p.fotoPath)
        assertEquals("Fernando", p.autor)
        assertEquals(3, p.autorNivel)
        assertTrue(p.id.isNotBlank())          // UUID local para idempotencia
        assertTrue(p.creadoEn > 0)
    }

    @Test
    fun `dos reportes seguidos no comparten el id local`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns false
        val ids = mutableListOf<ReportePendiente>()
        coEvery { ColaReportes.encolar(capture(ids)) } just Runs

        guardar(); guardar()

        assertEquals(2, ids.map { it.id }.toSet().size)
    }

    // ---------- degradación elegante ----------

    @Test
    fun `sin sesion activa el guardado falla en vez de crear un reporte huerfano`() {
        every { FirebaseAuth.getInstance() } returns authFake(uid = null)
        every { SincronizadorReportes.hayInternet() } returns false

        val e = assertThrows(IllegalStateException::class.java) { runBlocking { guardar() } }
        assertEquals("No hay sesión activa", e.message)
    }

    @Test
    fun `si no se puede leer el nombre visible el reporte igual se guarda sin autor`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns false
        coEvery { perfil.nombreVisible() } throws RuntimeException("sin permisos")
        val capturado = slot<ReportePendiente>()
        coEvery { ColaReportes.encolar(capture(capturado)) } just Runs

        guardar()

        assertEquals("", capturado.captured.autor)
    }

    @Test
    fun `si no se puede leer el nivel del autor se sella el nivel 1`() = runBlocking {
        every { SincronizadorReportes.hayInternet() } returns false
        coEvery { fuente.nivelUsuario() } throws RuntimeException("sin red")
        val capturado = slot<ReportePendiente>()
        coEvery { ColaReportes.encolar(capture(capturado)) } just Runs

        guardar()

        assertEquals(1, capturado.captured.autorNivel)
    }

    // ---------- análisis ----------

    @Test
    fun `analizar delega en la fuente de vision sin tocar nada`() = runBlocking {
        val esperado = ResultadoAnalisis("Basura acumulada", 86, Severidad.ALTA, 4.5)
        coEvery { analisis.analizar("/cache/foto.jpg") } returns esperado

        assertEquals(esperado, repo().analizar("/cache/foto.jpg"))
    }
}
