package com.moviles.minkia.ui.analisis

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.core.UiState
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.data.repository.ReporteRepository
import com.moviles.minkia.data.source.DetectorBasuraDataSource
import com.moviles.minkia.databinding.ActivityAnalisisBinding
import com.moviles.minkia.ui.formulario.FormularioActivity
import java.io.File

/**
 * Análisis con IA (mockup C10). Muestra la foto mientras "corre" la inferencia
 * en el dispositivo, anima el progreso y, al terminar, revela la detección y
 * habilita continuar al formulario. La inferencia real (YOLOv8) se enchufa en el
 * data source sin tocar esta pantalla.
 */
class AnalisisActivity : BaseActivity<ActivityAnalisisBinding>() {

    private val viewModel: AnalisisViewModel by viewModels {
        AnalisisViewModel.Factory(
            ReporteRepository(DetectorBasuraDataSource(applicationContext))
        )
    }

    private var rutaFoto: String? = null
    private var animador: ValueAnimator? = null

    override fun inflateBinding(inflater: LayoutInflater) = ActivityAnalisisBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        aplicarInsetSuperior(binding.topBar)
        aplicarInsetInferior(binding.analisisRoot) // el botón/banner esquiva la barra de gestos
        barrasClaras() // fondo verde profundo de punta a punta: iconos en claro
        binding.btnVolver.setOnClickListener { finish() }

        rutaFoto = intent.getStringExtra(EXTRA_FOTO)
        rutaFoto?.let { binding.ivFoto.setImageURI(Uri.fromFile(File(it))) }

        observar()
        animarProgreso()
        viewModel.analizar(rutaFoto.orEmpty())
    }

    private fun observar() {
        viewModel.estado.observe(this) { estado ->
            when (estado) {
                is UiState.Loading -> Unit // el progreso ya está animando
                is UiState.Success -> mostrarResultado(estado.data)
                is UiState.Error -> mostrarError()
            }
        }
    }

    /** Anima la barra 0..95 durante la inferencia; el 100 se fija al terminar. */
    private fun animarProgreso() {
        animador = ValueAnimator.ofInt(0, 95).apply {
            duration = 2200
            addUpdateListener {
                val v = it.animatedValue as Int
                binding.progreso.progress = v
                binding.tvPorcentaje.text = getString(R.string.analisis_porcentaje, v)
            }
            start()
        }
    }

    private fun mostrarResultado(resultado: ResultadoAnalisis) {
        animador?.cancel()
        binding.progreso.progress = 100
        binding.tvPorcentaje.text = getString(R.string.analisis_porcentaje, 100)

        binding.chipEstado.setText(R.string.analisis_listo)
        binding.tvEstadoTexto.setText(R.string.analisis_estado_listo)

        binding.chipDeteccion.text =
            getString(R.string.analisis_deteccion, resultado.tipo, resultado.confianza)
        binding.chipDeteccion.visibility = View.VISIBLE

        binding.btnContinuar.visibility = View.VISIBLE
        binding.btnContinuar.setOnClickListener { irAFormulario(resultado) }
    }

    private fun mostrarError() {
        animador?.cancel()
        binding.tvEstadoTexto.text = getString(R.string.error_generico)
        binding.btnContinuar.visibility = View.VISIBLE
        binding.btnContinuar.setText(R.string.analisis_reintentar)
        binding.btnContinuar.setOnClickListener {
            binding.btnContinuar.visibility = View.GONE
            binding.btnContinuar.setText(R.string.analisis_continuar)
            animarProgreso()
            viewModel.analizar(rutaFoto.orEmpty())
        }
    }

    private fun irAFormulario(resultado: ResultadoAnalisis) {
        startActivity(
            Intent(this, FormularioActivity::class.java)
                .putExtra(EXTRA_FOTO, rutaFoto)
                .putExtra(FormularioActivity.EXTRA_TIPO, resultado.tipo)
                .putExtra(FormularioActivity.EXTRA_CONFIANZA, resultado.confianza)
                .putExtra(FormularioActivity.EXTRA_SEVERIDAD, resultado.severidad.name)
                .putExtra(FormularioActivity.EXTRA_AREA, resultado.areaM2)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        animador?.cancel()
    }

    companion object {
        const val EXTRA_FOTO = "extra_foto"
    }
}
