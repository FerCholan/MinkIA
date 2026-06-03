package com.moviles.minkia.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.databinding.ActivityAdminMainBinding

/**
 * Actividad raíz del módulo administrador (Épica 5). Orquesta la navegación
 * inferior entre Panel (A01), Mapa/Puntos críticos (A03), Rutas (A05) y
 * Alertas (A02). Cada sección es un fragment.
 */
class AdminMainActivity : BaseActivity<ActivityAdminMainBinding>() {

    override fun inflateBinding(inflater: LayoutInflater) = ActivityAdminMainBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        // Edge-to-edge real (igual que el módulo ciudadano): el contenido sube tras
        // las barras. El root solo padea los lados; el inset superior fluye al header
        // de cada fragment (su degradé andino sube tras la status bar y baja solo el
        // contenido con aplicarInsetSuperior) y el inferior lo absorbe el nav. Los
        // headers son verde oscuro, así que los íconos de la status bar van en CLARO
        // y los de la nav bar (sobre el nav claro) en OSCURO. Edge-to-edge ya lo
        // activó BaseActivity; acá solo el contraste de iconos y el reparto de insets.
        barrasCabeceraVerde()
        ViewCompat.setOnApplyWindowInsetsListener(binding.adminRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, right = bars.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.adminNav) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }

        if (savedInstanceState == null) {
            mostrar(PanelFragment())
            binding.adminNav.selectedItemId = R.id.admin_panel
        }

        binding.adminNav.setOnItemSelectedListener { item ->
            val fragment: Fragment? = when (item.itemId) {
                R.id.admin_panel -> PanelFragment()
                R.id.admin_mapa -> PuntosCriticosFragment()
                R.id.admin_rutas -> RutasFragment()
                R.id.admin_alertas -> AlertasFragment()
                else -> null
            }
            fragment?.let { mostrar(it); true } ?: false
        }
    }

    private fun mostrar(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.adminContainer, fragment)
            .commit()
    }
}
