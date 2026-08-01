package com.moviles.minkia.ui.misdatos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetInferior
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.databinding.FragmentMisDatosBinding

/**
 * Pantalla "Mis datos" (C17b), destino de nav_ciudadano. Edita el DNI y el
 * APODO, y permite ACTIVAR/desactivar el uso del apodo en los reportes
 * (privacidad). Todo opcional. Se abre desde el menú de cuenta en Perfil.
 *
 * Sin argumentos: MisDatosActivity ya se abría sin extras (todo sale de
 * [MisDatosViewModel], que no cambia salvo por cómo se lo obtiene acá abajo).
 */
class MisDatosFragment : BaseFragment<FragmentMisDatosBinding>() {

    private val viewModel: MisDatosViewModel by viewModels { MisDatosViewModel.Factory() }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMisDatosBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.toolbar.aplicarInsetSuperior()
        binding.misDatosRoot.aplicarInsetInferior()
        // Cabecera verde: ya la fija MainActivity una sola vez para todo el host
        // (ver MainActivity.onViewReady); acá no hace falta repetirla.
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.btnGuardar.setOnClickListener { guardar() }
        observarViewModel()
    }

    /**
     * Tres flujos independientes del ViewModel: los datos iniciales (a pintar en los
     * campos), los errores de validación (de campo) y el resultado de guardar (que
     * decide si la pantalla cierra o sigue abierta).
     */
    private fun observarViewModel() {
        viewModel.datos.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                // Sin skeleton en esta pantalla: mientras carga se ven los campos vacíos.
                is UiState.Loading -> Unit
                is UiState.Success -> mostrarDatos(estado.data)
                // No debería pasar (cargar() ya resuelve cada campo a un default), pero
                // si pasara se ignora: perfil progresivo, ningún dato es indispensable.
                is UiState.Error -> Unit
            }
        }

        viewModel.errores.observe(viewLifecycleOwner) { errores ->
            binding.tilDni.error = errores.dni?.let { getString(it) }
            binding.tilApodo.error = errores.apodo?.let { getString(it) }
        }

        viewModel.estado.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> binding.btnGuardar.isEnabled = false
                is UiState.Success -> when (estado.data) {
                    ResultadoGuardar.Guardado -> {
                        Toast.makeText(requireContext(), R.string.editar_dni_guardado, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    // No es un error de red: el apodo sigue libre para otro vecino, no para este.
                    ResultadoGuardar.ApodoOcupado -> {
                        binding.tilApodo.error = getString(R.string.editar_nickname_ocupado)
                        binding.btnGuardar.isEnabled = true
                    }
                }
                is UiState.Error -> {
                    Toast.makeText(requireContext(), R.string.error_generico, Toast.LENGTH_LONG).show()
                    binding.btnGuardar.isEnabled = true
                }
            }
        }
    }

    private fun mostrarDatos(datos: DatosPersonales) {
        binding.etDni.setText(datos.dni)
        binding.etApodo.setText(datos.apodo)
        binding.swUsarApodo.isChecked = datos.usarApodo
    }

    private fun guardar() {
        viewModel.guardar(
            dni = binding.etDni.text?.toString()?.trim().orEmpty(),
            apodo = binding.etApodo.text?.toString()?.trim().orEmpty(),
            usarApodo = binding.swUsarApodo.isChecked
        )
    }
}
