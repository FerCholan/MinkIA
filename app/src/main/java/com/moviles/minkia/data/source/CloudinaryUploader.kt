package com.moviles.minkia.data.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
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

    private val cliente = OkHttpClient()

    suspend fun subir(fotoPath: String?): String = withContext(Dispatchers.IO) {
        if (fotoPath.isNullOrBlank()) return@withContext ""
        val archivo = File(fotoPath)
        if (!archivo.exists()) return@withContext ""
        try {
            val cuerpo = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .addFormDataPart("file", archivo.name, archivo.asRequestBody("image/jpeg".toMediaType()))
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
}
