package com.moviles.minkia.ui.registro

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetInferior
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.core.barrasCabeceraVerde
import com.moviles.minkia.core.mostrarError
import com.moviles.minkia.core.ocultarError
import com.moviles.minkia.core.transicionFundido
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.data.model.Usuario
import com.moviles.minkia.databinding.FragmentRegistroBinding
import com.moviles.minkia.ui.MainActivity
import com.moviles.minkia.ui.common.AccesoGoogle
import com.moviles.minkia.ui.permisos.RuteoPermisos
import kotlinx.coroutines.launch

/**
 * Pantalla de registro (mockup C04), destino del grafo nav_auth. Igual que el
 * login, observa dos flujos del [RegistroViewModel] (errores de campo y estado
 * de la creación de cuenta) y se limita a leer los campos, disparar la acción y
 * pintar cada estado. La barra superior permite volver al login.
 */
class RegistroFragment : BaseFragment<FragmentRegistroBinding>() {

    private val viewModel: RegistroViewModel by viewModels { RegistroViewModel.Factory() }
    private val prefs by lazy { PreferenciasRepository.create(requireContext()) }
    private val accesoGoogle by lazy { AccesoGoogle(requireContext()) }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRegistroBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.toolbar.aplicarInsetSuperior() // toolbar verde tras la status bar
        binding.registroRoot.aplicarInsetInferior() // el form esquiva la barra de gestos
        requireActivity().barrasCabeceraVerde()
        configurarEntradas()
        configurarTerminos()
        configurarAcciones()
        observarViewModel()
    }

    private fun configurarEntradas() {
        // Limpia el error del campo apenas el usuario empieza a corregirlo.
        binding.etNombre.doOnTextChanged { _, _, _, _ -> binding.tilNombre.error = null }
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null }
        binding.etDni.doOnTextChanged { _, _, _, _ -> binding.tilDni.error = null }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null }
        binding.cbTerminos.setOnCheckedChangeListener { _, marcado ->
            if (marcado) binding.tvErrorTerminos.visibility = View.GONE
        }
    }

    /** Convierte "Términos y Condiciones" y "Política de Datos" en enlaces que abren Términos. */
    private fun configurarTerminos() {
        val texto = getString(R.string.registro_terminos)
        val spannable = SpannableString(texto)
        enlazarTerminos(spannable, texto, getString(R.string.registro_terminos_link))
        enlazarTerminos(spannable, texto, getString(R.string.registro_privacidad_link))
        binding.tvTerminos.text = spannable
        binding.tvTerminos.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun enlazarTerminos(spannable: SpannableString, texto: String, sub: String) {
        val inicio = texto.indexOf(sub)
        if (inicio < 0) return
        // Se captura el Context una sola vez: el ClickableSpan puede disparar su
        // callback más tarde, y ahí adentro ya no hay forma directa de pedir
        // requireContext() (no es una función miembro del fragment).
        val contexto = requireContext()
        val span = object : ClickableSpan() {
            override fun onClick(widget: View) {
                findNavController().navigate(R.id.action_registro_a_terminos)
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = ContextCompat.getColor(contexto, R.color.verde_bosque)
                ds.isUnderlineText = false
                ds.isFakeBoldText = true
            }
        }
        spannable.setSpan(span, inicio, inicio + sub.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun configurarAcciones() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.btnRegistrar.setOnClickListener {
            viewModel.registrar(
                nombre = binding.etNombre.text?.toString().orEmpty(),
                email = binding.etEmail.text?.toString().orEmpty(),
                dni = binding.etDni.text?.toString().orEmpty(),
                password = binding.etPassword.text?.toString().orEmpty(),
                aceptaTerminos = binding.cbTerminos.isChecked
            )
        }

        binding.btnGoogle.setOnClickListener { accederConGoogle() }

        // Volver al login (de donde venimos): pop del back stack del grafo, no
        // una Activity nueva (misma idea que antes con finish()).
        binding.btnIniciaSesion.setOnClickListener { findNavController().popBackStack() }
    }

    private fun observarViewModel() {
        // viewLifecycleOwner (no "this"): ver nota en LoginFragment/PerfilFragment.
        viewModel.errores.observe(viewLifecycleOwner) { errores ->
            binding.tilNombre.error = errores.nombre?.let { getString(it) }
            binding.tilEmail.error = errores.email?.let { getString(it) }
            binding.tilDni.error = errores.dni?.let { getString(it) }
            binding.tilPassword.error = errores.password?.let { getString(it) }
            binding.tvErrorTerminos.visibility = if (errores.terminos) View.VISIBLE else View.GONE
        }

        viewModel.estado.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    mostrarCargando(true)
                    binding.bannerError.ocultarError()
                }
                is UiState.Success -> entrar(estado.data)
                is UiState.Error -> {
                    mostrarCargando(false)
                    binding.bannerError.mostrarError(estado.mensaje)
                }
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.btnRegistrar.isEnabled = !cargando
        // Bloquear también los accesos alternos: evita un segundo flujo de Google
        // encima del primero y navegar a Login con un registro en vuelo.
        binding.btnGoogle.isEnabled = !cargando
        binding.btnIniciaSesion.isEnabled = !cargando
        binding.btnRegistrar.setText(
            if (cargando) R.string.registro_registrando else R.string.registro_boton
        )
    }

    /** Registro/login con Google: mismo flujo que el login (reusa entrar() en Success).
     *  En viewLifecycleOwner.lifecycleScope: toca `binding` antes y después del
     *  suspend del selector de cuentas (ver nota equivalente en LoginFragment). */
    private fun accederConGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                mostrarCargando(true)
                binding.bannerError.ocultarError()
                val idToken = accesoGoogle.obtenerIdToken(getString(R.string.default_web_client_id))
                viewModel.iniciarSesionConGoogle(idToken)
            } catch (e: GetCredentialCancellationException) {
                mostrarCargando(false) // el usuario cerró el selector: sin error
            } catch (e: NoCredentialException) {
                mostrarCargando(false)
                binding.bannerError.mostrarError("No hay cuentas de Google en este dispositivo.")
            } catch (e: Exception) {
                mostrarCargando(false)
                binding.bannerError.mostrarError("No se pudo acceder con Google. Intenta de nuevo.")
            }
        }
    }

    /**
     * Guarda la sesión de la cuenta recién creada y rutea según [RuteoPermisos]:
     * cuenta nueva es siempre "primera vez", así que normalmente cae en Permisos
     * (otro fragment del mismo grafo). Sin Toast de bienvenida: la app saluda en
     * Inicio. Sin binding involucrado: alcanza con lifecycleScope.
     */
    private fun entrar(usuario: Usuario) {
        lifecycleScope.launch {
            prefs.guardarSesion(usuario)
            when (RuteoPermisos.destinoCiudadano(requireContext(), prefs)) {
                RuteoPermisos.DestinoCiudadano.INICIO -> salirA(MainActivity::class.java)
                RuteoPermisos.DestinoCiudadano.PERMISOS ->
                    findNavController().navigate(R.id.action_registro_a_permisos)
            }
        }
    }

    /** Sale del grafo de auth hacia una Activity real de la app (fuera de nav_auth). */
    private fun salirA(destino: Class<*>) {
        startActivity(Intent(requireContext(), destino))
        requireActivity().transicionFundido()
        requireActivity().finishAffinity() // cierra el flujo previo; el destino queda arriba.
    }
}
