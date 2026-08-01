package com.moviles.minkia.data.model

/**
 * Desenlace REAL de registrar un reporte. Existe porque antes `guardar()` devolvía
 * siempre un [Reporte] con su ticket, sin importar qué había pasado con el envío:
 * si Firestore lo rechazaba de forma permanente (regla de seguridad, dato inválido,
 * sesión vencida) el reporte no se enviaba NI se encolaba, y aun así el vecino veía
 * la pantalla de confirmación con un ticket. O sea, la app le decía "tu alerta llegó
 * al equipo de gestión" sobre un reporte que no existía en ningún lado.
 *
 * Ahora el repositorio declara cuál de los tres desenlaces ocurrió y la interfaz
 * puede decir la verdad en cada caso.
 */
sealed interface ResultadoRegistro {

    /** Guardado en el servidor. El vecino puede darle seguimiento ya mismo. */
    data class Enviado(val reporte: Reporte) : ResultadoRegistro

    /**
     * Guardado en la cola local, todavía sin llegar al servidor (sin conexión o
     * fallo transitorio). Se enviará solo cuando vuelva la red: el ticket es
     * válido, pero conviene avisar que la sincronización está pendiente.
     */
    data class Pendiente(val reporte: Reporte) : ResultadoRegistro

    /**
     * Rechazo permanente: no se envió ni se encoló, porque reintentarlo nunca
     * funcionaría. NO debe mostrarse una confirmación con ticket.
     */
    data class Error(val mensaje: String) : ResultadoRegistro
}
