package com.moviles.minkia.data.source.yolo

import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.model.Severidad
import kotlin.math.roundToInt

/**
 * Una detección ya decodificada, en coordenadas normalizadas [0,1]: esquina
 * superior-izquierda (x1,y1) e inferior-derecha (x2,y2) respecto al encuadre.
 */
data class Deteccion(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val score: Float
) {
    /** Área normalizada: fracción del encuadre que ocupa esta caja. */
    val area: Float
        get() = (x2 - x1).coerceAtLeast(0f) * (y2 - y1).coerceAtLeast(0f)
}

/**
 * Postprocesado PURO de la salida cruda de YOLOv8 (sin Android, sin TFLite, por eso
 * es testeable en JVM). Decodifica el tensor [1, 5, 8400], filtra por confianza,
 * aplica NMS y traduce el resultado al dominio de la app (ResultadoAnalisis).
 *
 * Layout de la salida: "atributo primero". Para la caja i (0..numCajas-1):
 *   cx    = salida[0*numCajas + i]
 *   cy    = salida[1*numCajas + i]
 *   w     = salida[2*numCajas + i]
 *   h     = salida[3*numCajas + i]
 *   score = salida[4*numCajas + i]
 * Las coordenadas vienen normalizadas [0,1] respecto al input de 640.
 *
 * Severidad por ÁREA cubierta del encuadre: >40% ALTA, 15-40% MEDIA, <15% BAJA.
 */
class PostprocesoYolo(
    private val umbralConfianza: Float = 0.40f,
    private val umbralNms: Float = 0.45f,
    private val areaEncuadreM2: Double = 6.0,
    private val numCajas: Int = 8400
) {

    /** Decodifica la salida cruda a las detecciones que superan el umbral de confianza. */
    fun decodificar(salida: FloatArray): List<Deteccion> {
        val detecciones = ArrayList<Deteccion>()
        for (i in 0 until numCajas) {
            val score = salida[4 * numCajas + i]
            if (score < umbralConfianza) continue
            val cx = salida[0 * numCajas + i]
            val cy = salida[1 * numCajas + i]
            val w = salida[2 * numCajas + i]
            val h = salida[3 * numCajas + i]
            detecciones += Deteccion(
                x1 = cx - w / 2f,
                y1 = cy - h / 2f,
                x2 = cx + w / 2f,
                y2 = cy + h / 2f,
                score = score
            )
        }
        return detecciones
    }

    /** Intersección sobre unión (IoU) de dos cajas. 0 si no se solapan. */
    fun iou(a: Deteccion, b: Deteccion): Float {
        val ix1 = maxOf(a.x1, b.x1)
        val iy1 = maxOf(a.y1, b.y1)
        val ix2 = minOf(a.x2, b.x2)
        val iy2 = minOf(a.y2, b.y2)
        val ancho = (ix2 - ix1).coerceAtLeast(0f)
        val alto = (iy2 - iy1).coerceAtLeast(0f)
        val interseccion = ancho * alto
        val union = a.area + b.area - interseccion
        return if (union <= 0f) 0f else interseccion / union
    }

    /**
     * Non-Max Suppression: ordena por score y descarta las cajas que se solapan
     * demasiado (IoU > umbral) con una ya conservada. Elimina las detecciones
     * duplicadas que apuntan a la misma basura.
     */
    fun nms(detecciones: List<Deteccion>): List<Deteccion> {
        val ordenadas = detecciones.sortedByDescending { it.score }
        val conservadas = ArrayList<Deteccion>()
        for (candidata in ordenadas) {
            val seSolapa = conservadas.any { iou(it, candidata) > umbralNms }
            if (!seSolapa) conservadas += candidata
        }
        return conservadas
    }

    /** Pipeline completo: salida cruda del modelo -> ResultadoAnalisis del dominio. */
    fun procesar(salida: FloatArray): ResultadoAnalisis {
        val detecciones = nms(decodificar(salida))

        if (detecciones.isEmpty()) {
            return ResultadoAnalisis(
                tipo = "Sin residuos detectados",
                confianza = 0,
                severidad = Severidad.BAJA,
                areaM2 = 0.0,
                esBasura = false // no hay basura en la foto: no se puede reportar
            )
        }

        // Cobertura: fracción del encuadre cubierta. Se suman las áreas (clamp a 1.0);
        // tras NMS el solapamiento residual es bajo, así que es una estimación razonable.
        val cobertura = detecciones.sumOf { it.area.toDouble() }.coerceIn(0.0, 1.0)

        val severidad = when {
            cobertura > 0.40 -> Severidad.ALTA
            cobertura >= 0.15 -> Severidad.MEDIA
            else -> Severidad.BAJA
        }

        val confianza = (detecciones.maxOf { it.score } * 100).roundToInt().coerceIn(0, 100)

        return ResultadoAnalisis(
            tipo = "Basura acumulada",
            confianza = confianza,
            severidad = severidad,
            // Estimación: NO son m2 reales (no hay calibración de cámara), es la
            // cobertura mapeada a un encuadre típico asumido (areaEncuadreM2).
            areaM2 = cobertura * areaEncuadreM2
        )
    }
}
