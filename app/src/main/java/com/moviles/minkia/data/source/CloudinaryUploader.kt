package com.moviles.minkia.data.source

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Sube fotos a Cloudinary con un "unsigned upload preset" (free tier, sin plan de
 * pago y sin backend). El preset es público por diseño: no hay secretos en la app.
 * Devuelve la URL https de la imagen, o "" si falla (la subida es best-effort: el
 * reporte se guarda igual sin foto).
 */
object CloudinaryUploader {

    // Cloud name del Dashboard. El preset es UNSIGNED (público por diseño).
    // NO va el API Secret acá: en una app cliente sería un agujero de seguridad.
    private const val CLOUD_NAME = "dfi7mgtnk"
    private const val UPLOAD_PRESET = "minkia_reportes"
    private const val TAG = "CloudinaryMinkIA"

    // Cliente COMPARTIDO (ver ClienteHttp): su pool de conexiones se vacía
    // cuando el vecino cambia de red, que es lo que hacía fallar la primera
    // subida después de salir del wifi.
    private val cliente get() = ClienteHttp.instancia

    suspend fun subir(fotoPath: String?): String = withContext(Dispatchers.IO) {
        if (fotoPath.isNullOrBlank()) return@withContext ""
        val archivo = File(fotoPath)
        if (!archivo.exists()) return@withContext ""
        try {
            // La foto sale del sensor a resolución máxima (CAPTURE_MODE_MAXIMIZE_QUALITY):
            // varios MB que con datos móviles no llegan nunca. Se manda una versión
            // reducida; si la reducción falla, se manda el archivo original.
            val reducida = comprimir(archivo)
            val parteFoto = reducida?.toRequestBody("image/jpeg".toMediaType())
                ?: archivo.asRequestBody("image/jpeg".toMediaType())

            val cuerpo = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .addFormDataPart("file", archivo.name, parteFoto)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload")
                .post(cuerpo)
                .build()

            cliente.newCall(request).execute().use { respuesta ->
                val cuerpoResp = respuesta.body?.string().orEmpty()
                if (!respuesta.isSuccessful) {
                    Log.e(TAG, "Cloudinary HTTP ${respuesta.code}: $cuerpoResp")
                    return@withContext ""
                }
                val url = JSONObject(cuerpoResp).optString("secure_url", "")
                Log.d(TAG, "Cloudinary OK -> $url")
                url
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloudinary excepción", e)
            "" // best-effort: sin foto, pero el reporte se guarda igual
        }
    }

    /**
     * Reduce la foto a algo que un vecino pueda subir con datos móviles: lado
     * mayor a [LADO_MAX] y JPEG al [CALIDAD] %. Una captura de 12 MP pesa entre 3
     * y 6 MB; así queda en unos pocos cientos de KB, y para ver un foco de basura
     * en el panel del municipio sobra.
     *
     * Devuelve null si no se pudo (foto corrupta, memoria insuficiente): el que
     * llama sube el archivo original en ese caso.
     *
     * OJO con la orientación: el JPEG del sensor viene derecho pero con la
     * rotación anotada en su EXIF, y decodificar a Bitmap + recomprimir TIRA ese
     * EXIF. Sin rotar los píxeles a mano acá, toda foto vertical llegaría acostada
     * al panel del municipio. Por eso se lee la etiqueta antes y se aplica.
     */
    private fun comprimir(archivo: File): ByteArray? = try {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, medidas)

        val opciones = BitmapFactory.Options().apply {
            inSampleSize = factorDeMuestreo(medidas.outWidth, medidas.outHeight)
        }
        val bitmap = BitmapFactory.decodeFile(archivo.path, opciones)
        if (bitmap == null) null else {
            val derecho = enderezar(bitmap, archivo)
            ByteArrayOutputStream().use { salida ->
                derecho.compress(Bitmap.CompressFormat.JPEG, CALIDAD, salida)
                derecho.recycle()
                salida.toByteArray()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo comprimir la foto; se sube el original", e)
        null
    } catch (e: OutOfMemoryError) {
        // Una foto grande en un teléfono con poca RAM: no se cae la app por esto.
        Log.e(TAG, "Sin memoria al comprimir la foto; se sube el original", e)
        null
    }

    /**
     * Potencia de 2 con la que decodificar para que el lado mayor no supere
     * [LADO_MAX]. BitmapFactory solo submuestrea de a potencias de 2, y hacerlo al
     * decodificar (y no después) es lo que evita cargar la foto entera en memoria.
     */
    private fun factorDeMuestreo(ancho: Int, alto: Int): Int {
        var factor = 1
        var mayor = maxOf(ancho, alto)
        while (mayor / 2 >= LADO_MAX) {
            mayor /= 2
            factor *= 2
        }
        return factor
    }

    /** Aplica al bitmap la rotación que el EXIF del archivo declara. */
    private fun enderezar(bitmap: Bitmap, archivo: File): Bitmap {
        val grados = when (
            ExifInterface(archivo.path)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matriz = Matrix().apply { postRotate(grados) }
        val rotado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
        if (rotado != bitmap) bitmap.recycle()
        return rotado
    }

    /** Lado mayor de la foto que se sube, en píxeles. */
    private const val LADO_MAX = 1600

    /** Calidad JPEG de la foto que se sube (0-100). */
    private const val CALIDAD = 82
}
