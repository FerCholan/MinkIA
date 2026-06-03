package com.moviles.minkia.ui.configuracion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.databinding.ActivityConfiguracionBinding
import com.moviles.minkia.ui.login.LoginActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Configuración (mockup C18). Preferencias de notificaciones, datos/conexión y
 * generales. Los switches se leen y persisten con DataStore vía
 * [PreferenciasRepository], así sobreviven a rotaciones y reinicios.
 */
class ConfiguracionActivity : BaseActivity<ActivityConfiguracionBinding>() {

    private val prefs by lazy { PreferenciasRepository.create(this) }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityConfiguracionBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetSuperior(binding.toolbar)
        aplicarInsetInferior(binding.configRoot) // el último ítem esquiva la barra de gestos
        barrasCabeceraVerde() // toolbar verde arriba, fondo claro abajo
        binding.toolbar.setNavigationOnClickListener { finish() }

        configurarSwitches()
        binding.filaIdioma.setOnClickListener { proximamente() }
        binding.filaAcerca.setOnClickListener { proximamente() }
        binding.btnCerrar.setOnClickListener { cerrarSesion() }
    }

    /**
     * Pinta el estado guardado de cada switch y recién después engancha los
     * listeners, para que setear el valor inicial no dispare una escritura.
     */
    private fun configurarSwitches() {
        lifecycleScope.launch {
            binding.swEstado.isChecked = prefs.notificacionesEstado.first()
            binding.swNovedades.isChecked = prefs.notificacionesNovedades.first()
            binding.swWifi.isChecked = prefs.descargaSoloWifi.first()

            binding.swEstado.setOnCheckedChangeListener { _, activo ->
                lifecycleScope.launch { prefs.setNotificacionesEstado(activo) }
            }
            binding.swNovedades.setOnCheckedChangeListener { _, activo ->
                lifecycleScope.launch { prefs.setNotificacionesNovedades(activo) }
            }
            binding.swWifi.setOnCheckedChangeListener { _, activo ->
                lifecycleScope.launch { prefs.setDescargaSoloWifi(activo) }
            }
        }
    }

    private fun proximamente() {
        Toast.makeText(this, R.string.login_proximamente, Toast.LENGTH_SHORT).show()
    }

    private fun cerrarSesion() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
