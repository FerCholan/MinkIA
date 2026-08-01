package com.moviles.minkia.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.load
import com.moviles.minkia.R

/**
 * Vista reutilizable para mostrar la foto de un foco/reporte en CUALQUIER pantalla
 * (Detalle, lista "Mis reportes", Inicio, validación admin) y hasta como miniatura
 * en filas de RecyclerView. Es una Custom View (no un Fragment) justamente para
 * poder vivir dentro de un item reciclable sin pelearse con el FragmentManager.
 *
 * - `cargar(url)` baja la imagen con Coil. Si la URL viene vacía (reporte sin
 *   foto), cae al ícono de cámara como placeholder, igual que el estado anterior.
 * - `permitirAmpliar = true` hace que tocar la foto la abra a pantalla completa.
 */
class FotoReporteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val imagen = ImageView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        contentDescription = context.getString(R.string.foto_reporte_desc)
    }

    /** Si está activo, tocar la foto la abre a pantalla completa (solo si hay foto). */
    var permitirAmpliar: Boolean = false

    private var urlActual: String? = null

    init {
        addView(imagen)
        setOnClickListener {
            if (permitirAmpliar && !urlActual.isNullOrBlank()) ampliar(urlActual!!)
        }
    }

    /** Carga la foto del reporte; URL vacía -> placeholder de cámara. */
    fun cargar(url: String?) {
        urlActual = url
        if (url.isNullOrBlank()) {
            mostrarPlaceholder()
            return
        }
        imagen.scaleType = ImageView.ScaleType.CENTER_CROP
        imagen.colorFilter = null
        isClickable = permitirAmpliar
        imagen.load(url) {
            crossfade(true)
            // Si la URL falla (red, 404, borrada en Cloudinary), no dejamos un
            // hueco: volvemos al placeholder de cámara.
            listener(onError = { _, _ -> mostrarPlaceholder() })
        }
    }

    private fun mostrarPlaceholder() {
        imagen.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imagen.setImageResource(R.drawable.ic_camara)
        imagen.setColorFilter(ContextCompat.getColor(context, R.color.verde_hoja_claro))
        isClickable = false
    }

    /** Abre la foto a pantalla completa, fondo negro, tap para cerrar. */
    private fun ampliar(url: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val visor = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = context.getString(R.string.foto_reporte_desc)
            setOnClickListener { dialog.dismiss() }
            load(url)
        }
        dialog.setContentView(visor)
        dialog.show()
    }
}
