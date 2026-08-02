package com.moviles.minkia.data.source

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Medicion del tiempo de inferencia del modelo YOLOv8 sobre el dispositivo, para
 * sustentar el requerimiento no funcional RNF02 (la deteccion se resuelve en menos
 * de tres segundos).
 *
 * Es una prueba INSTRUMENTADA: corre sobre el dispositivo o el emulador, con el
 * .tflite real cargado desde assets y el interprete real de LiteRT. No sustituye
 * la medicion sobre un telefono fisico de gama baja, pero produce cifras
 * concretas y reproducibles en lugar de la formula "del orden de segundos".
 *
 * Se ejecuta con:
 *   gradlew connectedDebugAndroidTest
 * y los tiempos quedan en logcat bajo la etiqueta [ETIQUETA].
 *
 * La foto de prueba (benchmark_basura.jpg) es una fotografia real de un tacho
 * desbordado: se usa la misma imagen en todas las repeticiones para que la unica
 * variable sea el tiempo de ejecucion.
 */
@RunWith(AndroidJUnit4::class)
class InferenciaBenchmarkTest {

    @Test
    fun mide_el_tiempo_de_inferencia_en_frio_y_en_caliente() = runBlocking {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val ruta = copiarFotoDePrueba(contexto)
        val detector = DetectorBasuraDataSource(contexto)

        // Primera ejecucion: incluye el mapeo del .tflite y la creacion del
        // interprete, que solo ocurren una vez por proceso. Se informa aparte
        // porque no representa el costo de un analisis corriente.
        var resultadoFrio: com.moviles.minkia.data.model.ResultadoAnalisis? = null
        val enFrio = measureTimeMillis { resultadoFrio = detector.analizar(ruta) }
        Log.i(ETIQUETA, "ejecucion=1 tipo=frio ms=$enFrio")

        val calientes = ArrayList<Long>(REPETICIONES)
        repeat(REPETICIONES) { i ->
            val ms = measureTimeMillis { detector.analizar(ruta) }
            calientes += ms
            Log.i(ETIQUETA, "ejecucion=${i + 2} tipo=caliente ms=$ms")
        }

        val promedio = calientes.average()
        val minimo = calientes.min()
        val maximo = calientes.max()

        Log.i(ETIQUETA, "RESUMEN frio=$enFrio promedio=%.0f min=$minimo max=$maximo n=${calientes.size}"
            .format(promedio))
        Log.i(ETIQUETA, "DETECCION esBasura=${resultadoFrio?.esBasura} " +
            "confianza=${resultadoFrio?.confianza} " +
            "cobertura=${resultadoFrio?.porcentajeCobertura} " +
            "severidad=${resultadoFrio?.severidad}")

        // RNF02: el umbral se evalua sobre las ejecuciones en caliente, que son
        // las que vive el vecino a partir del segundo reporte de la sesion.
        assertTrue(
            "El tiempo maximo en caliente ($maximo ms) supera el umbral de RNF02",
            maximo < UMBRAL_RNF02_MS
        )
    }

    /** Vuelca la foto de prueba de los assets de la prueba a un archivo real. */
    private fun copiarFotoDePrueba(contexto: Context): String {
        val assetsDePrueba = InstrumentationRegistry.getInstrumentation().context.assets
        val destino = File(contexto.cacheDir, FOTO)
        assetsDePrueba.open(FOTO).use { entrada ->
            destino.outputStream().use { salida -> entrada.copyTo(salida) }
        }
        return destino.absolutePath
    }

    companion object {
        const val ETIQUETA = "MinkIaBenchmark"
        private const val FOTO = "benchmark_basura.jpg"
        private const val REPETICIONES = 9
        private const val UMBRAL_RNF02_MS = 3_000L

        @BeforeClass
        @JvmStatic
        fun aviso() {
            Log.i(ETIQUETA, "Inicio del benchmark de inferencia")
        }
    }
}
