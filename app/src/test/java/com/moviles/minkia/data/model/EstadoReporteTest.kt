package com.moviles.minkia.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lógica pura del filtro de la lista de reportes: resueltos vs pendientes.
 * Sin Android, sin reglas: solo el enum.
 */
class EstadoReporteTest {

    @Test
    fun `RESUELTO es resuelto y no pendiente`() {
        assertTrue(EstadoReporte.RESUELTO.esResuelto)
        assertFalse(EstadoReporte.RESUELTO.esPendiente)
    }

    @Test
    fun `RECIBIDO y EN_PROCESO son pendientes`() {
        assertTrue(EstadoReporte.RECIBIDO.esPendiente)
        assertTrue(EstadoReporte.EN_PROCESO.esPendiente)
    }

    @Test
    fun `los estados pendientes no estan resueltos`() {
        assertFalse(EstadoReporte.RECIBIDO.esResuelto)
        assertFalse(EstadoReporte.EN_PROCESO.esResuelto)
    }

    @Test
    fun `DUPLICADO no es ni pendiente ni resuelto`() {
        assertFalse(EstadoReporte.DUPLICADO.esPendiente)
        assertFalse(EstadoReporte.DUPLICADO.esResuelto)
    }

    @Test
    fun `ANULADO no es ni pendiente ni resuelto`() {
        assertFalse(EstadoReporte.ANULADO.esPendiente)
        assertFalse(EstadoReporte.ANULADO.esResuelto)
    }
}
