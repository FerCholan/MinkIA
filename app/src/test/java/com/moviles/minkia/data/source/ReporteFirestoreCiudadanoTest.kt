package com.moviles.minkia.data.source

import com.google.firebase.Timestamp
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.model.TipoNotificacion
import com.moviles.minkia.util.authFake
import com.moviles.minkia.util.conDocumento
import com.moviles.minkia.util.documentoAusente
import com.moviles.minkia.util.documentoFake
import com.moviles.minkia.util.firestoreCon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Lógica del LADO CIUDADANO que hoy vive dentro de la fuente de datos: mis
 * reportes, resumen del inicio, focos del mapa, notificaciones derivadas del
 * estado y la gamificación del perfil. Es la superficie de negocio más grande de
 * la app y la que no tenía ninguna prueba: acá se verifica el CÁLCULO, no
 * Firestore.
 */
class ReporteFirestoreCiudadanoTest {

    private fun ts(ms: Long) = Timestamp(Date(ms))

    private fun reporte(
        id: String = "d1",
        userId: String = "u1",
        estado: String = EstadoReporte.RECIBIDO.name,
        severidad: String = Severidad.MEDIA.name,
        creadoEn: Long = 1_000L,
        vararg extra: Pair<String, Any?>
    ) = documentoFake(
        id,
        "userId" to userId,
        "estado" to estado,
        "severidad" to severidad,
        "creadoEn" to ts(creadoEn),
        *extra
    )

    // ---------- misReportes ----------

    @Test
    fun `sin sesion activa misReportes devuelve lista vacia`() = runBlocking {
        val ds = ReporteFirestoreDataSource(authFake(uid = null), firestoreCon("reportes", emptyList()))
        assertTrue(ds.misReportes().isEmpty())
    }

    @Test
    fun `misReportes ordena del mas nuevo al mas viejo`() = runBlocking {
        val docs = listOf(
            reporte(id = "viejo", creadoEn = 1_000L, extra = arrayOf("ticket" to "#MK-1")),
            reporte(id = "nuevo", creadoEn = 9_000L, extra = arrayOf("ticket" to "#MK-9")),
            reporte(id = "medio", creadoEn = 5_000L, extra = arrayOf("ticket" to "#MK-5"))
        )
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        assertEquals(listOf("#MK-9", "#MK-5", "#MK-1"), ds.misReportes().map { it.ticket })
    }

    @Test
    fun `misReportes aplica los valores por defecto cuando el documento viene incompleto`() = runBlocking {
        // Documento sin severidad, sin estado, sin area, sin autor: el mapeo no debe
        // romper ni inventar; debe caer a MEDIA / RECIBIDO / 0.0 / nivel 1.
        val docs = listOf(documentoFake("d1", "userId" to "u1"))
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        val r = ds.misReportes().single()
        assertEquals(Severidad.MEDIA, r.severidad)
        assertEquals(EstadoReporte.RECIBIDO, r.estado)
        assertEquals(0.0, r.areaM2, 1e-9)
        assertEquals(0, r.vecinos)
        assertEquals(1, r.autorNivel)
        assertEquals("", r.ticket)
        assertEquals("—", r.fechaTexto) // sin fecha, no muestra basura
    }

    @Test
    fun `un estado viejo que ya no existe cae a RECIBIDO y no rompe`() = runBlocking {
        // EN_RUTA se eliminó del enum: los reportes viejos deben seguir abriéndose.
        val docs = listOf(reporte(estado = "EN_RUTA"))
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        assertEquals(EstadoReporte.RECIBIDO, ds.misReportes().single().estado)
    }

    // ---------- obtenerResumen (C07) ----------

    @Test
    fun `el resumen cuenta activos solo entre pendientes y no entre duplicados ni anulados`() = runBlocking {
        val docs = listOf(
            reporte(id = "1", estado = EstadoReporte.RECIBIDO.name),
            reporte(id = "2", estado = EstadoReporte.EN_PROCESO.name),
            reporte(id = "3", estado = EstadoReporte.RESUELTO.name),
            reporte(id = "4", estado = EstadoReporte.DUPLICADO.name),
            reporte(id = "5", estado = EstadoReporte.ANULADO.name)
        )
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        val resumen = ds.obtenerResumen()
        assertEquals(2, resumen.puntosActivos)  // RECIBIDO + EN_PROCESO
        assertEquals(1, resumen.resueltos)
    }

    @Test
    fun `el resumen cuenta como tuyos solo los reportes del usuario logueado`() = runBlocking {
        val docs = listOf(
            reporte(id = "1", userId = "u1"),
            reporte(id = "2", userId = "otro"),
            reporte(id = "3", userId = "u1")
        )
        val ds = ReporteFirestoreDataSource(authFake(uid = "u1"), firestoreCon("reportes", docs))

        assertEquals(2, ds.obtenerResumen().tusReportes)
    }

    @Test
    fun `el resumen muestra como maximo 6 zonas criticas y son las mas recientes`() = runBlocking {
        val docs = (1..10).map {
            reporte(id = "d$it", creadoEn = it * 1_000L, extra = arrayOf("zona" to "Zona$it"))
        }
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        val resumen = ds.obtenerResumen()
        assertEquals(6, resumen.puntosCercanos.size)
        assertEquals(6, resumen.focosCerca)
        // De la zona reportada más recientemente a la más vieja.
        assertEquals(listOf("d10", "d9", "d8", "d7", "d6", "d5"), resumen.puntosCercanos.map { it.id })
    }

    @Test
    fun `los focos de la misma zona se agrupan en UNA tarjeta con el conteo real`() = runBlocking {
        // Antes cada foco viajaba con cantidadReportes = 1 fijo y el Inicio decía
        // literalmente "· 1 reportes" en todas las tarjetas, siempre.
        val docs = listOf(
            reporte(id = "a", creadoEn = 1_000L, extra = arrayOf("zona" to "Bellamar", "direccion" to "Vieja 1")),
            reporte(id = "b", creadoEn = 5_000L, extra = arrayOf("zona" to "Bellamar", "direccion" to "Nueva 5")),
            reporte(id = "c", creadoEn = 3_000L, extra = arrayOf("zona" to "Bellamar", "direccion" to "Media 3")),
            reporte(id = "d", creadoEn = 9_000L, extra = arrayOf("zona" to "Miraflores"))
        )
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        val puntos = ds.obtenerResumen().puntosCercanos
        assertEquals(2, puntos.size) // dos zonas, no cuatro tarjetas

        val bellamar = puntos.first { it.cantidadReportes == 3 }
        assertEquals("Nueva 5", bellamar.direccion) // la del reporte más nuevo del grupo
        assertEquals("d", puntos.first().id)        // Miraflores es la zona más reciente
    }

    @Test
    fun `la zona critica se pinta con la peor severidad de sus focos`() = runBlocking {
        val docs = listOf(
            reporte(id = "a", severidad = Severidad.BAJA.name, extra = arrayOf("zona" to "Z")),
            reporte(id = "b", severidad = Severidad.ALTA.name, extra = arrayOf("zona" to "Z"))
        )
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        assertEquals(Severidad.ALTA, ds.obtenerResumen().puntosCercanos.single().severidad)
    }

    @Test
    fun `el resumen usa el nombre visible del usuario y cae a Vecino si no hay ninguno`() = runBlocking {
        val db = firestoreCon("reportes", emptyList())
        assertEquals("Ana", ReporteFirestoreDataSource(authFake(displayName = "Ana"), db).obtenerResumen().nombre)
        assertEquals(
            "fernando",
            ReporteFirestoreDataSource(authFake(email = "fernando@mail.com"), db).obtenerResumen().nombre
        )
        assertEquals("Vecino", ReporteFirestoreDataSource(authFake(uid = null), db).obtenerResumen().nombre)
    }

    // ---------- obtenerFocosMapa (C08) ----------

    @Test
    fun `el mapa descarta los focos sin coordenadas y los estados terminales`() = runBlocking {
        val docs = listOf(
            reporte(id = "ok", extra = arrayOf("latitud" to -9.07, "longitud" to -78.59)),
            reporte(id = "sinCoords"), // sin lat/lng: no se puede pintar
            reporte(
                id = "resuelto",
                estado = EstadoReporte.RESUELTO.name,
                extra = arrayOf("latitud" to -9.0, "longitud" to -78.0)
            ),
            reporte(
                id = "anulado",
                estado = EstadoReporte.ANULADO.name,
                extra = arrayOf("latitud" to -9.0, "longitud" to -78.0)
            )
        )
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        assertEquals(listOf("ok"), ds.obtenerFocosMapa().map { it.id })
    }

    @Test
    fun `si el foco no trae zona se deriva de la primera parte de la direccion`() = runBlocking {
        val docs = listOf(
            reporte(
                id = "a",
                extra = arrayOf(
                    "latitud" to -9.0, "longitud" to -78.0,
                    "direccion" to "Miraflores Alto, Chimbote, Áncash"
                )
            ),
            reporte(
                id = "b",
                extra = arrayOf(
                    "latitud" to -9.0, "longitud" to -78.0,
                    "zona" to "Bellamar", "direccion" to "Otra cosa, Chimbote"
                )
            ),
            // Sin zona y sin dirección: no puede quedar vacío, cae a "Chimbote".
            reporte(id = "c", extra = arrayOf("latitud" to -9.0, "longitud" to -78.0))
        )
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        val zonas = ds.obtenerFocosMapa().associate { it.id to it.zona }
        assertEquals("Miraflores Alto", zonas["a"])
        assertEquals("Bellamar", zonas["b"]) // la zona guardada gana sobre la dirección
        assertEquals("Chimbote", zonas["c"])
    }

    // ---------- notificaciones (C16) ----------

    @Test
    fun `sin sesion activa no hay notificaciones`() = runBlocking {
        val ds = ReporteFirestoreDataSource(authFake(uid = null), firestoreCon("reportes", emptyList()))
        assertTrue(ds.notificacionesCiudadano().isEmpty())
    }

    @Test
    fun `cada estado del reporte genera su propio tipo de notificacion`() = runBlocking {
        val ahora = System.currentTimeMillis()
        val docs = listOf(
            reporte(id = "1", estado = EstadoReporte.RESUELTO.name, creadoEn = ahora - 5_000),
            reporte(id = "2", estado = EstadoReporte.EN_PROCESO.name, creadoEn = ahora - 6_000),
            reporte(id = "3", estado = EstadoReporte.DUPLICADO.name, creadoEn = ahora - 7_000),
            reporte(id = "4", estado = EstadoReporte.ANULADO.name, creadoEn = ahora - 8_000),
            reporte(id = "5", estado = EstadoReporte.RECIBIDO.name, creadoEn = ahora - 9_000)
        )
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        assertEquals(
            listOf(
                TipoNotificacion.RESUELTO,
                TipoNotificacion.VALIDADO,
                TipoNotificacion.UNIFICADO,
                TipoNotificacion.UNIFICADO,
                TipoNotificacion.VALIDADO
            ),
            ds.notificacionesCiudadano().map { it.tipo }
        )
    }

    @Test
    fun `las notificaciones se agrupan en HOY o ESTA SEMANA segun las ultimas 24 horas`() = runBlocking {
        val ahora = System.currentTimeMillis()
        val docs = listOf(
            reporte(id = "reciente", creadoEn = ahora - 60_000),                 // hace 1 min
            reporte(id = "viejo", creadoEn = ahora - 48L * 60 * 60 * 1000)       // hace 2 días
        )
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        val grupos = ds.notificacionesCiudadano().map { it.grupo }
        assertEquals(listOf("HOY", "ESTA SEMANA"), grupos)
    }

    @Test
    fun `la notificacion sin direccion no muestra un hueco vacio`() = runBlocking {
        val docs = listOf(reporte(estado = EstadoReporte.RESUELTO.name))
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        assertTrue(ds.notificacionesCiudadano().single().texto.contains("tu zona"))
    }

    // ---------- gamificación / perfil (C17) ----------

    /** Perfil de un vecino con [reportes] reportes, de los cuales [resueltos] están resueltos. */
    private suspend fun perfilCon(reportes: Int, resueltos: Int, nombre: String? = "Fernando Cholán") =
        ReporteFirestoreDataSource(
            auth = authFake(displayName = nombre),
            db = conDocumento(
                db = firestoreCon(
                    "reportes",
                    (1..reportes).map {
                        reporte(
                            id = "d$it",
                            estado = if (it <= resueltos) EstadoReporte.RESUELTO.name
                            else EstadoReporte.RECIBIDO.name
                        )
                    }
                ),
                coleccion = "usuarios",
                id = "u1",
                doc = documentoAusente() // sin nombre guardado: cae al de Auth
            )
        ).obtenerPerfil()

    @Test
    fun `un vecino sin reportes arranca en nivel 1 con 0 puntos`() = runBlocking {
        val p = perfilCon(reportes = 0, resueltos = 0)
        assertEquals(0, p.puntosMinka)
        assertEquals(1, p.nivel)
        assertEquals(200, p.puntosNivelObjetivo)
        assertEquals("Nivel 1 · Brote Ciudadano", p.nivelTexto)
        assertTrue(p.faltanTexto.contains("200"))
    }

    @Test
    fun `los puntos son 20 por reporte mas 30 por reporte resuelto`() = runBlocking {
        val p = perfilCon(reportes = 5, resueltos = 2) // 5*20 + 2*30 = 160
        assertEquals(160, p.puntosMinka)
        assertEquals(5, p.reportes)
        assertEquals(2, p.resueltos)
        assertEquals(1, p.nivel)
        assertTrue(p.faltanTexto.contains("40")) // 200 - 160
    }

    @Test
    fun `se sube de nivel cada 200 puntos`() = runBlocking {
        assertEquals(2, perfilCon(reportes = 10, resueltos = 0).nivel) // 200 pts justos
        assertEquals(3, perfilCon(reportes = 20, resueltos = 0).nivel) // 400 pts
    }

    @Test
    fun `al subir de nivel la barra de progreso vuelve a arrancar vacia`() = runBlocking {
        // Antes se publicaba el total acumulado contra `nivel * 200`, así que apenas
        // llegabas al nivel 2 la barra ya aparecía por la mitad (200/400), y en el
        // nivel 3 arrancaba en dos tercios. Nunca se veía vacía después del nivel 1.
        val recienNivel2 = perfilCon(reportes = 10, resueltos = 0) // 200 pts exactos
        assertEquals(2, recienNivel2.nivel)
        assertEquals(0, recienNivel2.puntosNivelActual)
        assertEquals(200, recienNivel2.puntosNivelObjetivo)
        assertTrue(recienNivel2.faltanTexto.contains("200"))

        val recienNivel3 = perfilCon(reportes = 20, resueltos = 0) // 400 pts exactos
        assertEquals(0, recienNivel3.puntosNivelActual)
    }

    @Test
    fun `la barra mide el avance DENTRO del nivel sin perder los puntos totales`() = runBlocking {
        val p = perfilCon(reportes = 13, resueltos = 0) // 260 pts -> nivel 2, 60 en el nivel

        assertEquals(260, p.puntosMinka)     // el total sigue siendo el total
        assertEquals(60, p.puntosNivelActual) // pero la barra muestra 60 de 200
        assertEquals(200, p.puntosNivelObjetivo)
        assertTrue(p.faltanTexto.contains("140"))
    }

    @Test
    fun `en el nivel maximo la barra se llena y el texto lo dice`() = runBlocking {
        val p = perfilCon(reportes = 50, resueltos = 0) // 1000 pts -> tope
        assertEquals(5, p.nivel)
        assertEquals("Guardián Global", p.rol)
        assertEquals(p.puntosNivelObjetivo, p.puntosNivelActual) // barra al 100 %
        assertTrue(p.faltanTexto.contains("máximo"))
    }

    @Test
    fun `el nivel nunca pasa de 5 por mas reportes que haga`() = runBlocking {
        assertEquals(5, perfilCon(reportes = 200, resueltos = 100).nivel)
    }

    @Test
    fun `las iniciales salen del nombre y nunca quedan vacias`() = runBlocking {
        assertEquals("FC", perfilCon(0, 0, nombre = "Fernando Cholán").iniciales)
        assertEquals("AN", perfilCon(0, 0, nombre = "ana").iniciales)          // un solo nombre
        assertEquals("VE", perfilCon(0, 0, nombre = null).iniciales)           // cae a "Vecino"
        assertEquals("VE", perfilCon(0, 0, nombre = "   ").iniciales)          // nombre en blanco
    }

    @Test
    fun `nivelUsuario usa la misma formula que el perfil`() = runBlocking {
        val docs = (1..10).map { reporte(id = "d$it") } // 200 pts -> nivel 2
        val ds = ReporteFirestoreDataSource(authFake(), firestoreCon("reportes", docs))

        assertEquals(2, ds.nivelUsuario())
    }

    @Test
    fun `sin sesion nivelUsuario es 1`() = runBlocking {
        val ds = ReporteFirestoreDataSource(authFake(uid = null), firestoreCon("reportes", emptyList()))
        assertEquals(1, ds.nivelUsuario())
    }

    @Test
    fun `el perfil prefiere el nombre guardado en usuarios sobre el de Auth`() = runBlocking {
        val db = conDocumento(
            firestoreCon("reportes", emptyList()),
            coleccion = "usuarios",
            id = "u1",
            doc = documentoFake("u1", "nombre" to "Fernando Cholán")
        )
        val perfil = ReporteFirestoreDataSource(authFake(displayName = "otro"), db).obtenerPerfil()

        assertEquals("Fernando Cholán", perfil.nombre)
    }

    @Test
    fun `sin sesion el perfil no explota y queda en cero`() = runBlocking {
        val ds = ReporteFirestoreDataSource(authFake(uid = null), firestoreCon("reportes", emptyList()))
        val p = ds.obtenerPerfil()
        assertEquals(0, p.reportes)
        assertEquals(1, p.nivel)
        assertEquals("Vecino", p.nombre)
    }
}
