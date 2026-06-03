package com.moviles.minkia.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.ActivityAdminValidacionBinding

/**
 * Detalle y validación de un reporte para el administrador (mockup A04). Muestra
 * la detección de la IA, los reportes unificados y permite validar/duplicar/
 * rechazar. Las acciones son mock por ahora.
 */
class ValidacionActivity : BaseActivity<ActivityAdminValidacionBinding>() {

    override fun inflateBinding(inflater: LayoutInflater) = ActivityAdminValidacionBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetSuperior(binding.toolbar)
        aplicarInsetInferior(binding.validacionRoot) // los botones esquivan la barra de gestos
        barrasCabeceraVerde() // toolbar verde arriba, fondo claro abajo
        binding.toolbar.setNavigationOnClickListener { finish() }

        val direccion = intent.getStringExtra(EXTRA_DIRECCION) ?: "—"
        val agrupados = intent.getIntExtra(EXTRA_AGRUPADOS, 1)
        val severidad = runCatching { Severidad.valueOf(intent.getStringExtra(EXTRA_SEVERIDAD) ?: "") }
            .getOrDefault(Severidad.MEDIA)

        binding.toolbar.title = getString(R.string.admin_val_titulo, "#MK-2041")
        binding.chipIa.text = getString(R.string.admin_val_ia, "Basura", 86)
        binding.chipSeveridad.text = getString(R.string.reportes_severidad, severidad.name)
        binding.chipSeveridad.setTextColor(ContextCompat.getColor(this, colorSev(severidad)))
        binding.chipReportes.text = getString(R.string.detalle_vecinos, agrupados)
        binding.tvUnificados.text = getString(R.string.admin_val_unificados, agrupados)
        binding.tvUbicacion.text = direccion
        binding.tvReportado.text = getString(R.string.detalle_vecinos, agrupados)

        binding.btnValidar.setOnClickListener { cerrarCon(R.string.admin_val_validado_ok) }
        binding.btnDuplicado.setOnClickListener { cerrarCon(R.string.admin_val_duplicado) }
        binding.btnRechazar.setOnClickListener { cerrarCon(R.string.admin_val_rechazar) }
    }

    private fun cerrarCon(mensaje: Int) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun colorSev(s: Severidad) = when (s) {
        Severidad.ALTA -> R.color.sev_alta
        Severidad.MEDIA -> R.color.sev_media
        Severidad.BAJA -> R.color.sev_baja
    }

    companion object {
        const val EXTRA_DIRECCION = "extra_direccion"
        const val EXTRA_SEVERIDAD = "extra_severidad"
        const val EXTRA_AGRUPADOS = "extra_agrupados"
    }
}
