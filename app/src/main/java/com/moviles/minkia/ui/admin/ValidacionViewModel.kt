package com.moviles.minkia.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.moviles.minkia.core.BaseViewModel
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.data.repository.AdminRepository

/**
 * Acción de moderación terminal disparada desde [ValidacionActivity]. Las cuatro
 * comparten el mismo desenlace visible (la Activity cierra al terminar); solo
 * cambia el texto del aviso de éxito según cuál se ejecutó.
 */
enum class AccionModeracion { RESOLVER, VALIDAR, DUPLICADO, ANULAR }

/**
 * ViewModel de moderación de un reporte (A04). Cada acción escribe en Firestore
 * vía [AdminRepository]; todas requieren rol admin (lo exigen las reglas de
 * Firestore, no este ViewModel). Las acciones terminales comparten [uiState]
 * porque la vista reacciona igual a las cuatro. Cambiar el nivel es distinto
 * (actualiza el chip sin cerrar la pantalla), por eso tiene su propio [nivelState].
 *
 * [alerta] y [cargar] son la ÚNICA parte nueva de este ViewModel (FASE 3 de la
 * migración a Navigation Component): ValidacionActivity nunca leía nada de acá,
 * pintaba directo los trece extras que le llegaban por Intent. Al matar esos
 * extras (nav_admin.xml declara un solo argumento "reporteId") la pantalla
 * necesita, por primera vez, pedirle la alerta completa al repositorio, así que
 * el ViewModel gana esta capacidad de lectura sin perder ninguna de escritura.
 */
class ValidacionViewModel(
    private val repository: AdminRepository = AdminRepository()
) : BaseViewModel() {

    private val _alerta = MutableLiveData<UiState<AlertaAdmin>>()
    val alerta: LiveData<UiState<AlertaAdmin>> = _alerta

    private val _uiState = MutableLiveData<UiState<AccionModeracion>>()
    val uiState: LiveData<UiState<AccionModeracion>> = _uiState

    private val _nivelState = MutableLiveData<UiState<Severidad>>()
    val nivelState: LiveData<UiState<Severidad>> = _nivelState

    /** Busca la alerta por id para pintar la pantalla (ver KDoc de la clase). */
    fun cargar(id: String) = loadInto(_alerta) {
        repository.obtenerAlertaPorId(id) ?: error("No se encontró el reporte $id")
    }

    /** Aprobar resuelto (-> RESUELTO): el foco fue limpiado. */
    fun resolver(id: String) = loadInto(_uiState) {
        repository.resolver(id)
        AccionModeracion.RESOLVER
    }

    /** Validar el reporte (-> EN_PROCESO). */
    fun validar(id: String) = loadInto(_uiState) {
        repository.validar(id)
        AccionModeracion.VALIDAR
    }

    /** Marcar como duplicado de otro reporte ya existente. */
    fun marcarDuplicado(id: String) = loadInto(_uiState) {
        repository.marcarDuplicado(id)
        AccionModeracion.DUPLICADO
    }

    /** Anular (troll/falso): marca ANULADO sin borrar. */
    fun anular(id: String) = loadInto(_uiState) {
        repository.anular(id)
        AccionModeracion.ANULAR
    }

    /** Publica la severidad nueva en éxito, para que la vista repinte el chip. */
    fun actualizarNivel(id: String, nuevo: Severidad) = loadInto(_nivelState) {
        repository.actualizarNivel(id, nuevo)
        nuevo
    }

    /** Factory para inyectar el repositorio sin librerías de DI. */
    class Factory(
        private val repository: AdminRepository = AdminRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ValidacionViewModel(repository) as T
        }
    }
}
