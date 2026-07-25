package com.moviles.minkia.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.UiState
import com.moviles.minkia.core.aplicarInsetsVerticales
import com.moviles.minkia.core.barraEstadoOscura
import com.moviles.minkia.core.mostrarError
import com.moviles.minkia.core.ocultarError
import com.moviles.minkia.core.transicionFundido
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.data.model.Usuario
import com.moviles.minkia.databinding.FragmentLoginBinding
import com.moviles.minkia.ui.MainActivity
import com.moviles.minkia.ui.common.AccesoGoogle
import com.moviles.minkia.ui.permisos.RuteoPermisos
import kotlinx.coroutines.launch

/**
 * Pantalla de login (mockup C03), destino del grafo nav_auth. Observa dos
 * flujos del [LoginViewModel]: los errores de validación de campo y el estado
 * de la autenticación. No contiene reglas de negocio: solo lee los campos,
 * dispara la acción y pinta cada estado.
 */
class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory() }
    private val prefs by lazy { PreferenciasRepository.create(requireContext()) }
    private val accesoGoogle by lazy { AccesoGoogle(requireContext()) }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.loginRoot.aplicarInsetsVerticales()
        requireActivity().barraEstadoOscura() // fondo claro (edge-to-edge ya viene de BaseActivity)
        configurarEntradas()
        configurarAcciones()
        observarViewModel()
    }

    private fun configurarEntradas() {
        // Limpia el error del campo (y el banner) apenas el usuario empieza a corregir.
        binding.etEmail.doOnTextChanged { _, _, _, _ -> binding.tilEmail.error = null; ocultarBanner() }
        binding.etPassword.doOnTextChanged { _, _, _, _ -> binding.tilPassword.error = null; ocultarBanner() }
    }

    private fun configurarAcciones() {
        binding.btnIniciar.setOnClickListener {
            viewModel.iniciarSesion(
                email = binding.etEmail.text?.toString().orEmpty(),
                password = binding.etPassword.text?.toString().orEmpty()
            )
        }

        binding.btnOlvidaste.setOnClickListener {
            findNavController().navigate(R.id.action_login_a_recuperar)
        }
        binding.btnRegistrate.setOnClickListener {
            findNavController().navigate(R.id.action_login_a_registro)
        }
        binding.btnGoogle.setOnClickListener { accederConGoogle() }
    }

    private fun observarViewModel() {
        // Con viewLifecycleOwner (no "this"): el fragment puede sobrevivir a su
        // vista, y observar con su propio lifecycle arriesgaría tocar `binding`
        // ya liberado en onDestroyView (ver BaseFragment / PerfilFragment).
        viewModel.errores.observe(viewLifecycleOwner) { errores ->
            binding.tilEmail.error = errores.email?.let { getString(it) }
            binding.tilPassword.error = errores.password?.let { getString(it) }
        }

        viewModel.estado.observe(viewLifecycleOwner) { estado ->
            when (estado) {
                is UiState.Loading -> {
                    mostrarCargando(true)
                    ocultarBanner()
                }
                is UiState.Success -> entrar(estado.data)
                is UiState.Error -> {
                    mostrarCargando(false)
                    mostrarErrorBanner(estado.mensaje)
                }
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.btnIniciar.isEnabled = !cargando
        binding.btnGoogle.isEnabled = !cargando
        binding.btnIniciar.setText(
            if (cargando) R.string.login_ingresando else R.string.login_iniciar
        )
    }

    /**
     * Guarda la sesión (para no volver a pedir login) y rutea por rol: el
     * administrador SALE del grafo de auth directo a MainActivity, que resuelve
     * su grafo de admin al leer la sesión; el ciudadano sigue la regla de
     * [RuteoPermisos], que hoy puede resolver en OTRO fragment del MISMO grafo
     * (Permisos) o en una salida externa (esa misma MainActivity, con su grafo
     * de ciudadano).
     * Sin Toast de bienvenida: la app ya saluda en Inicio (UX moderna). Sin
     * binding involucrado, por eso alcanza con lifecycleScope (no hace falta
     * viewLifecycleOwner.lifecycleScope acá).
     */
    private fun entrar(usuario: Usuario) {
        lifecycleScope.launch {
            prefs.guardarSesion(usuario)
            if (usuario.esAdmin) {
                // El admin no pasa por RuteoPermisos: esa regla es del priming de
                // permisos del flujo de reporte del ciudadano y no le aplica. Va
                // directo a MainActivity (ya no existe AdminMainActivity), que
                // resuelve el grafo de admin al leer la sesión recién guardada.
                salirA(MainActivity::class.java)
                return@launch
            }
            when (RuteoPermisos.destinoCiudadano(requireContext(), prefs)) {
                RuteoPermisos.DestinoCiudadano.INICIO -> salirA(MainActivity::class.java)
                RuteoPermisos.DestinoCiudadano.PERMISOS ->
                    findNavController().navigate(R.id.action_login_a_permisos)
            }
        }
    }

    /** Sale del grafo de auth hacia una Activity real de la app (fuera de nav_auth). */
    private fun salirA(destino: Class<*>) {
        startActivity(Intent(requireContext(), destino))
        requireActivity().transicionFundido()
        requireActivity().finishAffinity() // cierra todo el flujo de acceso; el destino queda arriba.
    }

    private fun mostrarErrorBanner(mensaje: String) = binding.bannerError.mostrarError(mensaje)

    private fun ocultarBanner() = binding.bannerError.ocultarError()

    /**
     * Flujo de Google: pide el ID token con Credential Manager y se lo pasa al
     * ViewModel, que lo cambia por una sesión (Loading/Success/Error igual que
     * el login normal, así reusa entrar()). Si el usuario cierra el selector,
     * no es error. Usa viewLifecycleOwner.lifecycleScope (no lifecycleScope a
     * secas) porque toca `binding` antes Y después del suspend de Credential
     * Manager: si la vista se destruye mientras el selector está abierto, la
     * corrutina debe cancelarse sola en vez de arriesgar un binding nulo.
     */
    private fun accederConGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                mostrarCargando(true)
                ocultarBanner()
                val idToken = accesoGoogle.obtenerIdToken(getString(R.string.default_web_client_id))
                viewModel.iniciarSesionConGoogle(idToken)
            } catch (e: GetCredentialCancellationException) {
                mostrarCargando(false) // el usuario cerró el selector: sin error
            } catch (e: NoCredentialException) {
                mostrarCargando(false)
                mostrarErrorBanner("No hay cuentas de Google en este dispositivo.")
            } catch (e: Exception) {
                mostrarCargando(false)
                mostrarErrorBanner("No se pudo acceder con Google. Intenta de nuevo.")
            }
        }
    }
}
