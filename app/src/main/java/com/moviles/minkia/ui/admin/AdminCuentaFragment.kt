package com.moviles.minkia.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetSuperior
import com.moviles.minkia.data.local.PreferenciasRepository
import com.moviles.minkia.databinding.FragmentAdminCuentaBinding
import com.moviles.minkia.ui.auth.AuthActivity
import kotlinx.coroutines.launch

/**
 * Cuenta del administrador (A07): lo básico del módulo admin. Muestra quién está
 * logueado (nombre/email/rol), la versión de la app y permite CERRAR SESIÓN. El
 * logout borra la sesión local (DataStore) Y la de Firebase Auth, y vuelve al login.
 */
class AdminCuentaFragment : BaseFragment<FragmentAdminCuentaBinding>() {

    private val prefs by lazy { PreferenciasRepository.create(requireContext()) }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAdminCuentaBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        binding.headerCuentaContenido.aplicarInsetSuperior()

        val user = auth.currentUser
        val email = user?.email.orEmpty()
        binding.tvNombre.text = user?.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").ifBlank { "Administrador" }
        binding.tvEmail.text = email.ifBlank { "—" }
        binding.tvVersion.text = "v" + versionApp()

        binding.filaAcerca.setOnClickListener { mostrarAcerca() }
        binding.btnCerrar.setOnClickListener { confirmarCerrar() }
    }

    private fun versionApp(): String = runCatching {
        requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    private fun mostrarAcerca() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.config_acerca_titulo)
            .setMessage(getString(R.string.config_acerca_mensaje, versionApp()))
            .setPositiveButton(R.string.config_cerrar_dialogo, null)
            .show()
    }

    private fun confirmarCerrar() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perfil_cerrar)
            .setMessage(R.string.admin_cuenta_cerrar_confirma)
            .setPositiveButton(R.string.perfil_cerrar) { _, _ -> cerrarSesion() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Cierra sesión de verdad: borra la sesión local (DataStore) Y la de Firebase
     * Auth, y arranca el login limpiando el back stack para que "atrás" no vuelva.
     */
    private fun cerrarSesion() {
        lifecycleScope.launch {
            prefs.cerrarSesion()
            auth.signOut()
            // Va directo a Login dentro del grafo de auth (sin pasar por
            // onboarding): mismo comportamiento que antes al lanzar LoginActivity
            // directamente. Ver AuthActivity.configurarDestinoInicial().
            startActivity(
                Intent(requireContext(), AuthActivity::class.java)
                    .putExtra(AuthActivity.EXTRA_OMITIR_ONBOARDING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            requireActivity().finish()
        }
    }
}
