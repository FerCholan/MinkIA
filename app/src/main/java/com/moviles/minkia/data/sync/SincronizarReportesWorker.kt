package com.moviles.minkia.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Reintento de la cola offline GARANTIZADO por el sistema.
 *
 * El callback de red de [com.moviles.minkia.MinkIaApplication] solo corre mientras
 * el proceso de la app está vivo. Si Android lo mata por memoria (lo normal cuando
 * el vecino deja el teléfono en el bolsillo), los reportes encolados se quedan en el
 * disco hasta que alguien vuelva a abrir la app: podían pasar días sin llegar a la
 * municipalidad aunque hubiera conexión de sobra.
 *
 * WorkManager persiste el trabajo en su propia base de datos y lo ejecuta cuando se
 * cumple la restricción de red, sobreviva o no el proceso, e incluso después de
 * reiniciar el teléfono. El callback se mantiene como atajo para el caso inmediato;
 * este worker es el respaldo que asegura la entrega.
 */
class SincronizarReportesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val enviados = SincronizadorReportes.sincronizar()
        Log.i(TAG, "sincronización en segundo plano: $enviados reporte(s) enviado(s)")
        Result.success()
    } catch (e: Exception) {
        // retry() y no failure(): el fallo acá es de red o de servidor, o sea
        // transitorio. WorkManager reintenta con backoff exponencial en vez de
        // abandonar el reporte. Los rechazos permanentes ya los descarta el propio
        // sincronizador (ver SincronizadorReportes.sincronizar).
        Log.w(TAG, "sincronización fallida, se reintentará", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "SyncWorker"

        /** Nombre del trabajo único: evita encolar N sincronizaciones en paralelo. */
        private const val TRABAJO = "sincronizar_reportes"

        /**
         * Programa el reintento. [ExistingWorkPolicy.KEEP] conserva el trabajo ya
         * encolado en vez de reemplazarlo: si el vecino crea tres reportes sin
         * conexión, basta con una sola sincronización pendiente, porque procesa toda
         * la cola de una pasada.
         */
        fun programar(context: Context) {
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val solicitud = OneTimeWorkRequestBuilder<SincronizarReportesWorker>()
                .setConstraints(restricciones)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SEGUNDOS, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(TRABAJO, ExistingWorkPolicy.KEEP, solicitud)
        }

        private const val BACKOFF_SEGUNDOS = 30L
    }
}
