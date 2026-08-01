package com.moviles.minkia.data.source

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.source.yolo.PostprocesoYolo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Inferencia REAL con el modelo YOLOv8 exportado a TFLite (best_float32.tflite en
 * assets/). Implementa el mismo contrato que el mock, así el repositorio, el
 * ViewModel y la UI no cambian.
 *
 * Pipeline:
 *   foto -> bitmap 640x640 -> tensor NHWC float32 normalizado [0,1]
 *        -> intérprete TFLite -> salida cruda [1,5,8400]
 *        -> PostprocesoYolo (decode + NMS + heurísticas) -> ResultadoAnalisis
 *
 * Acá solo vive la plomería de Android/TFLite. La matemática (decode/NMS/severidad)
 * está en [PostprocesoYolo], que es lógica pura y está cubierta por tests.
 */
class DetectorBasuraDataSource(
    private val context: Context,
    private val postproceso: PostprocesoYolo = PostprocesoYolo()
) : AnalisisDataSource {

    override suspend fun analizar(rutaFoto: String): ResultadoAnalisis =
        withContext(Dispatchers.Default) {
            // Decodifica submuestreado (cerca de 640) para NO cargar la foto full-res en RAM.
            val bitmap = decodificarEscalado(rutaFoto)
            val entrada = try {
                aTensor(bitmap)
            } finally {
                bitmap.recycle() // liberamos el bitmap apenas tenemos el tensor
            }

            val salida = Array(1) { Array(SALIDA_ATRIBUTOS) { FloatArray(SALIDA_CAJAS) } }

            // Dos analisis simultaneos (p. ej. al rotar la pantalla mid-inferencia) nunca
            // deben tocar el interprete a la vez -> crash nativo. El mutex lo impide.
            mutexInferencia.withLock {
                obtenerInterprete(context).run(entrada, salida)
            }

            postproceso.procesar(aplanar(salida))
        }

    /**
     * Decodifica la foto SUBMUESTREADA hasta cerca de 640 px y la escala exacto a
     * 640x640. Evita cargar en RAM el bitmap full-res del sensor (riesgo de OOM).
     * Escala directo (sin letterbox): como solo importa la PRESENCIA y la cobertura
     * de basura, la pequeña distorsión es aceptable.
     */
    private fun decodificarEscalado(rutaFoto: String): Bitmap {
        // Paso 1: leer SOLO las dimensiones (inJustDecodeBounds no aloca pixeles).
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(rutaFoto, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) {
            throw IOException("No se pudo abrir la foto: $rutaFoto")
        }
        // Paso 2: decodificar submuestreado (potencia de 2 cercana a 640, no la foto entera).
        val opciones = BitmapFactory.Options().apply {
            inSampleSize = calcularInSampleSize(limites.outWidth, limites.outHeight, TAM_ENTRADA)
        }
        val decodificado = BitmapFactory.decodeFile(rutaFoto, opciones)
            ?: throw IOException("No se pudo decodificar la foto: $rutaFoto")
        // Paso 3: escalar exacto a 640x640; reciclar el intermedio si es otra instancia.
        val escalado = Bitmap.createScaledBitmap(decodificado, TAM_ENTRADA, TAM_ENTRADA, true)
        if (escalado !== decodificado) decodificado.recycle()
        return escalado
    }

    /** Mayor potencia de 2 que mantiene ambos lados >= objetivo (submuestreo de decode). */
    private fun calcularInSampleSize(ancho: Int, alto: Int, objetivo: Int): Int {
        var sample = 1
        var w = ancho
        var h = alto
        while (w / 2 >= objetivo && h / 2 >= objetivo) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    /** Bitmap 640x640 -> ByteBuffer float32 [1,640,640,3], RGB, normalizado a [0,1]. */
    private fun aTensor(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(4 * TAM_ENTRADA * TAM_ENTRADA * 3)
            .order(ByteOrder.nativeOrder())

        val pixeles = IntArray(TAM_ENTRADA * TAM_ENTRADA)
        bitmap.getPixels(pixeles, 0, TAM_ENTRADA, 0, 0, TAM_ENTRADA, TAM_ENTRADA)

        for (pixel in pixeles) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
            buffer.putFloat((pixel and 0xFF) / 255f)          // B
        }
        buffer.rewind()
        return buffer
    }

    /** [1][5][8400] -> FloatArray plano con índice (atributo*cajas + caja). */
    private fun aplanar(salida: Array<Array<FloatArray>>): FloatArray {
        val plano = FloatArray(SALIDA_ATRIBUTOS * SALIDA_CAJAS)
        for (a in 0 until SALIDA_ATRIBUTOS) {
            for (i in 0 until SALIDA_CAJAS) {
                plano[a * SALIDA_CAJAS + i] = salida[0][a][i]
            }
        }
        return plano
    }

    companion object {
        private const val MODELO_ASSET = "best_float32.tflite"
        private const val TAM_ENTRADA = 640
        private const val SALIDA_ATRIBUTOS = 5    // cx, cy, w, h, score
        private const val SALIDA_CAJAS = 8400

        // UNA sola instancia del intérprete por proceso: el modelo es caro de crear y
        // mapea memoria nativa. Antes se construía uno por cada Activity de análisis y
        // NUNCA se cerraba -> fuga nativa acumulativa que podía agotar el heap nativo.
        // Ahora se comparte; el mutex (también compartido) serializa el run() entre
        // todas las llamadas porque Interpreter.run no es thread-safe.
        @Volatile
        private var interpreteCompartido: Interpreter? = null
        private val mutexInferencia = Mutex()

        private fun obtenerInterprete(context: Context): Interpreter =
            interpreteCompartido ?: synchronized(this) {
                interpreteCompartido
                    ?: Interpreter(cargarModelo(context.applicationContext, MODELO_ASSET))
                        .also { interpreteCompartido = it }
            }

        /** Mapea en memoria el .tflite desde assets (read-only, sin copiarlo). */
        private fun cargarModelo(context: Context, nombre: String): ByteBuffer {
            context.assets.openFd(nombre).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { canal ->
                    return canal.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    }
}
