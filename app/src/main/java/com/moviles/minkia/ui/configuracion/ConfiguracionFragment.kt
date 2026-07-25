package com.moviles.minkia.ui.configuracion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetInferior
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.data.local.ColaReportes
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.data.sync.SincronizadorReportes
import com.moviles.minkia.databinding.FragmentConfiguracionBinding
import com.moviles.minkia.ui.auth.AuthActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Configuración (mockup C18), destino de nav_ciudadano. Preferencias de
 * notificaciones, datos/conexión y generales. Los switches se leen y persisten
 * con DataStore vía [PreferenciasRepository], así sobreviven a rotaciones y
 * reinicios. Se abre desde el menú de cuenta en Perfil.
 *
 * Sin ViewModel: ConfiguracionActivity tampoco tenía uno (habla directo con
 * DataStore/Firebase), y esta fase no le agrega uno porque no hace falta ningún
 * dato por argumento. Sin extras antes ni ahora.
 */
class ConfiguracionFragment : BaseFragment<FragmentConfiguracionBinding>() {

    private val prefs by lazy { PreferenciasRepository.create(requireContext()) }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentConfiguracionBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.toolbar.aplicarInsetSuperior()
        binding.configRoot.aplicarInsetInferior() // el último ítem esquiva la barra de gestos
        // Cabecera verde: ya la fija MainActivity una sola vez para todo el host
        // (ver MainActivity.onViewReady); acá no hace falta repetirla.
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        configurarSwitches()
        binding.filaIdioma.setOnClickListener { mostrarIdioma() }
        binding.filaAcerca.setOnClickListener { mostrarAcerca() }
        binding.btnCerrar.setOnClickListener { cerrarSesion() }

        observarPendientes()
        binding.filaSincronizar.setOnClickListener { sincronizarAhora() }
    }

    /** Muestra el número REAL de reportes en cola (reactivo) y el texto de ayuda. */
    private fun observarPendientes() {
        lifecycleScope.launch {
            ColaReportes.cantidad.collect { n ->
                if (n > 0) {
                    binding.tvPendientes.visibility = View.VISIBLE
                    binding.tvPendientes.text = n.toString()
                    binding.tvSincronizarSub.setText(R.string.config_sync_pendientes)
                } else {
                    binding.tvPendientes.visibility = View.GONE
                    binding.tvSincronizarSub.setText(R.string.config_sync_al_dia)
                }
            }
        }
    }

    /** Reintenta el envío de la cola al toque. Si no hay nada o no hay red, avisa. */
    private fun sincronizarAhora() {
        if (ColaReportes.cantidad.value == 0) {
            Toast.makeText(requireContext(), R.string.config_sync_nada, Toast.LENGTH_SHORT).show()
            return
        }
        binding.tvSincronizarSub.setText(R.string.config_sync_sincronizando)
        lifecycleScope.launch {
            val enviados = SincronizadorReportes.sincronizar()
            val msg = when {
                enviados > 0 -> getString(R.string.config_sync_enviados, enviados)
                !SincronizadorReportes.hayInternet() -> getString(R.string.config_sync_sin_red)
                else -> getString(R.string.config_sync_pendientes)
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Diálogo "Acerca de" con la versión real de la app (sin BuildConfig). */
    private fun mostrarAcerca() {
        val ctx = requireContext()
        val version = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.config_acerca_titulo)
            .setMessage(getString(R.string.config_acerca_mensaje, version))
            .setPositiveButton(R.string.config_cerrar_dialogo, null)
            .show()
    }

    /** Diálogo informativo del idioma (la app es Español Perú). */
    private fun mostrarIdioma() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.config_idioma_titulo)
            .setMessage(R.string.config_idioma_mensaje)
            .setPositiveButton(R.string.config_cerrar_dialogo, null)
            .show()
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

    /**
     * Mismo patrón que PerfilFragment.cerrarSesion (el otro punto de logout):
     * borra la sesión local y la de Firebase, y sale del grafo del ciudadano
     * directo a Login (sin pasar por onboarding) relanzando AuthActivity con
     * FLAG_ACTIVITY_NEW_TASK/CLEAR_TASK. requireActivity().finish() cierra
     * MainActivity entera, no solo este fragment: es la salida del host completo.
     */
    private fun cerrarSesion() {
        lifecycleScope.launch {
            prefs.cerrarSesion() // borra la sesión local (DataStore)
            FirebaseAuth.getInstance().signOut() // cierra también la sesión de Firebase
            startActivity(
                Intent(requireContext(), AuthActivity::class.java)
                    .putExtra(AuthActivity.EXTRA_OMITIR_ONBOARDING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            requireActivity().finish()
        }
    }
}
