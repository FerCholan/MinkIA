package com.moviles.minkia.data.source.yolo

import com.moviles.minkia.data.model.Severidad
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la lógica PURA de postprocesado YOLOv8: decode del tensor [1,5,8400],
 * NMS y heurísticas de severidad/área. Sin Android, sin modelo: solo matemática.
 */
class PostprocesoYoloTest {

    /** Arma una salida cruda [5*n] con el layout "atributo primero" del modelo. */
    private fun salida(n: Int, vararg cajas: FloatArray): FloatArray {
        val out = FloatArray(5 * n)
        cajas.forEachIndexed { i, c ->
            out[0 * n + i] = c[0] // cx
            out[1 * n + i] = c[1] // cy
            out[2 * n + i] = c[2] // w
            out[3 * n + i] = c[3] // h
            out[4 * n + i] = c[4] // score
        }
        return out
    }

    private fun caja(cx: Float, cy: Float, w: Float, h: Float, score: Float) =
        floatArrayOf(cx, cy, w, h, score)

    // ---------- IoU ----------

    @Test
    fun `iou de cajas identicas es 1`() {
        val post = PostprocesoYolo()
        val a = Deteccion(0.1f, 0.1f, 0.5f, 0.5f, 0.9f)
        assertEquals(1.0f, post.iou(a, a), 1e-4f)
    }

    @Test
    fun `iou de cajas disjuntas es 0`() {
        val post = PostprocesoYolo()
        val a = Deteccion(0f, 0f, 0.2f, 0.2f, 0.9f)
        val b = Deteccion(0.5f, 0.5f, 0.8f, 0.8f, 0.9f)
        assertEquals(0.0f, post.iou(a, b), 1e-4f)
    }

    @Test
    fun `iou de solapamiento parcial conocido`() {
        val post = PostprocesoYolo()
        // a: [0,0,2,2] área 4 ; b: [1,1,3,3] área 4 ; intersección [1,1,2,2] = 1
        // unión = 4 + 4 - 1 = 7 ; iou = 1/7
        val a = Deteccion(0f, 0f, 2f, 2f, 0.9f)
        val b = Deteccion(1f, 1f, 3f, 3f, 0.9f)
        assertEquals(1f / 7f, post.iou(a, b), 1e-4f)
    }

    // ---------- decodificar ----------

    @Test
    fun `decodificar descarta cajas bajo el umbral de confianza`() {
        val post = PostprocesoYolo(umbralConfianza = 0.40f, numCajas = 2)
        val out = salida(
            n = 2,
            caja(0.5f, 0.5f, 0.2f, 0.2f, 0.80f), // pasa
            caja(0.5f, 0.5f, 0.2f, 0.2f, 0.30f)  // no pasa
        )
        val dets = post.decodificar(out)
        assertEquals(1, dets.size)
        assertEquals(0.80f, dets[0].score, 1e-4f)
    }

    @Test
    fun `decodificar lee el layout atributo-primero y convierte a xyxy`() {
        val post = PostprocesoYolo(umbralConfianza = 0.40f, numCajas = 1)
        // cx=0.5 cy=0.5 w=0.4 h=0.2  ->  x1=0.3 y1=0.4 x2=0.7 y2=0.6
        val out = salida(n = 1, caja(0.5f, 0.5f, 0.4f, 0.2f, 0.9f))
        val d = post.decodificar(out).single()
        assertEquals(0.3f, d.x1, 1e-4f)
        assertEquals(0.4f, d.y1, 1e-4f)
        assertEquals(0.7f, d.x2, 1e-4f)
        assertEquals(0.6f, d.y2, 1e-4f)
    }

    // ---------- NMS ----------

    @Test
    fun `nms elimina la duplicada solapada y conserva la de mayor score`() {
        val post = PostprocesoYolo(umbralNms = 0.45f)
        val fuerte = Deteccion(0f, 0f, 0.5f, 0.5f, 0.9f)
        val debilSolapada = Deteccion(0.02f, 0.02f, 0.52f, 0.52f, 0.6f) // IoU alto con fuerte
        val res = post.nms(listOf(debilSolapada, fuerte))
        assertEquals(1, res.size)
        assertEquals(0.9f, res[0].score, 1e-4f)
    }

    @Test
    fun `nms conserva dos cajas separadas`() {
        val post = PostprocesoYolo(umbralNms = 0.45f)
        val a = Deteccion(0f, 0f, 0.2f, 0.2f, 0.9f)
        val b = Deteccion(0.6f, 0.6f, 0.9f, 0.9f, 0.8f)
        val res = post.nms(listOf(a, b))
        assertEquals(2, res.size)
    }

    // ---------- procesar (heurísticas de dominio) ----------

    @Test
    fun `procesar sin detecciones devuelve resultado vacio`() {
        val post = PostprocesoYolo(numCajas = 1)
        val out = salida(n = 1, caja(0.5f, 0.5f, 0.2f, 0.2f, 0.10f)) // bajo umbral
        val r = post.procesar(out)
        assertEquals(0, r.confianza)
        assertEquals(Severidad.BAJA, r.severidad)
        assertEquals(0.0, r.areaM2, 1e-6)
    }

    @Test
    fun `procesar con cobertura alta da severidad ALTA`() {
        val post = PostprocesoYolo(numCajas = 1, areaEncuadreM2 = 6.0)
        // caja 0.7 x 0.7 = 0.49 del encuadre (> 0.40)
        val out = salida(n = 1, caja(0.5f, 0.5f, 0.7f, 0.7f, 0.95f))
        val r = post.procesar(out)
        assertEquals(Severidad.ALTA, r.severidad)
        assertEquals(95, r.confianza)
        assertEquals("Basura acumulada", r.tipo)
        assertEquals(0.49 * 6.0, r.areaM2, 1e-3)
    }

    @Test
    fun `procesar con cobertura media da severidad MEDIA`() {
        val post = PostprocesoYolo(numCajas = 1)
        // 0.5 x 0.5 = 0.25 del encuadre (entre 0.15 y 0.40)
        val out = salida(n = 1, caja(0.5f, 0.5f, 0.5f, 0.5f, 0.7f))
        val r = post.procesar(out)
        assertEquals(Severidad.MEDIA, r.severidad)
    }

    @Test
    fun `procesar con cobertura baja da severidad BAJA`() {
        val post = PostprocesoYolo(numCajas = 1)
        // 0.3 x 0.3 = 0.09 del encuadre (< 0.15)
        val out = salida(n = 1, caja(0.5f, 0.5f, 0.3f, 0.3f, 0.7f))
        val r = post.procesar(out)
        assertEquals(Severidad.BAJA, r.severidad)
    }
}
