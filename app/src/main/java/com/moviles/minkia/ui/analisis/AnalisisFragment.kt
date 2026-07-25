package com.moviles.minkia.ui.analisis

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import coil.load
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetInferior
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.barrasClaras
import com.moviles.minkia.data.model.ResultadoAnalisis
import com.moviles.minkia.databinding.FragmentAnalisisBinding
import com.moviles.minkia.ui.reporte.ReporteFlowViewModel
import java.io.File

/**
 * Análisis con IA (mockup C10), segundo paso del grafo nav_reporte. Muestra
 * la foto tomada en Captura mientras "corre" la inferencia en el dispositivo,
 * anima el progreso y, al terminar, revela la detección y habilita continuar
 * al formulario. La inferencia real (YOLOv8/TFLite) se enchufa en el data
 * source del [ReporteFlowViewModel] compartido, sin tocar este fragment: acá
 * solo se dispara y se observa Loading/Success/Error.
 */
class AnalisisFragment : BaseFragment<FragmentAnalisisBinding>() {

    // Mismo ViewModel que CapturaFragment (ver su KDoc): navGraphViewModels lo
    // busca por el NavBackStackEntry del grafo, que ya existe al llegar acá,
    // así que la fábrica no se llega a usar; se pasa solo para cumplir la
    // firma (ver ReporteFlowViewModel.Factory).
    private val flujo: ReporteFlowViewModel by navGraphViewModels(R.id.nav_reporte) {
        ReporteFlowViewModel.Factory(requireContext().applicationContext)
    }

    private var animador: ValueAnimator? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAnalisisBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.topBar.aplicarInsetSuperior()
        binding.analisisRoot.aplicarInsetInferior() // el botón/banner esquiva la barra de gestos
        requireActivity().barrasClaras() // fondo verde profundo de punta a punta: iconos en claro
        binding.btnVolver.setOnClickListener { findNavController().popBackStack() }

        // Coil decodifica en segundo plano y submuestrea al tamaño de la vista: no
        // bloquea el hilo principal ni carga la foto full-res del sensor en RAM.
        flujo.fotoPath?.let { binding.ivFoto.load(File(it)) }

        observar()
        animarProgreso()
        flujo.analizar()
    }

    private fun observar() {
        // viewLifecycleOwner: flujo es un ViewModel de GRAFO, vive más que la
        // vista de este fragment puntual. Observar con el fragment como owner
        // (o peor, sin especificar uno) dejaría un observer zombie después de
        // onDestroyView, que intentaría pintar un binding ya liberado la
        // próxima vez que este LiveData emita.
        flujo.analisis.observe(viewLifecycleOwner) { estado ->
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

        // Si el modelo NO detectó basura, no se puede reportar: se bloquea el avance
        // y se invita a retomar la foto (vuelve a la cámara, que sigue en el stack).
        if (!resultado.esBasura) {
            mostrarNoBasura()
            return
        }

        binding.chipEstado.setText(R.string.analisis_listo)
        binding.tvEstadoTexto.setText(R.string.analisis_estado_listo)

        binding.chipDeteccion.text =
            getString(R.string.analisis_deteccion, resultado.tipo, resultado.confianza)
        binding.chipDeteccion.visibility = View.VISIBLE

        binding.btnContinuar.visibility = View.VISIBLE
        binding.btnContinuar.setText(R.string.analisis_continuar)
        binding.btnContinuar.setOnClickListener { irAFormulario() }
    }

    /** No hay basura en la foto: se avisa y el único camino es retomar (no reportar). */
    private fun mostrarNoBasura() {
        binding.chipEstado.setText(R.string.analisis_no_basura_chip)
        binding.tvEstadoTexto.setText(R.string.analisis_no_basura_texto)
        binding.chipDeteccion.visibility = View.GONE
        binding.btnContinuar.visibility = View.VISIBLE
        binding.btnContinuar.setText(R.string.analisis_retomar)
        binding.btnContinuar.setOnClickListener { findNavController().popBackStack() } // vuelve a la cámara
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
            flujo.analizar()
        }
    }

    /** El resultado ya quedó en flujo.resultadoAnalisis (ver
     *  ReporteFlowViewModel.analizar): Formulario lo lee de ahí, no hace
     *  falta pasarlo por putExtra ni por argumento de navegación. */
    private fun irAFormulario() {
        findNavController().navigate(R.id.action_analisis_a_formulario)
    }

    /** Ver BaseFragment.onBindingDestroy: reemplaza al onDestroy() que tenía
     *  la Activity para no dejar el animador corriendo de más. */
    override fun onBindingDestroy() {
        animador?.cancel()
    }
}
