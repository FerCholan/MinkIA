package com.moviles.minkia.ui.reportes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.moviles.minkia.R
import com.moviles.minkia.data.model.EstadoReporte
import com.moviles.minkia.data.model.MiReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.ItemMiReporteBinding

/**
 * Adaptador del historial de reportes (C13). Pinta dirección, fecha/ticket y los
 * chips de estado y severidad con su color. Avisa el tap para abrir el detalle.
 */
class MiReporteAdapter(
    private val onClick: (View, MiReporte) -> Unit
) : ListAdapter<MiReporte, MiReporteAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMiReporteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: ItemMiReporteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(r: MiReporte) {
            val ctx = binding.root.context
            binding.tvDireccion.text = r.direccion
            binding.tvFecha.text = ctx.getString(R.string.reportes_fecha_ticket, r.fechaTexto, r.ticket)

            pintarEstado(binding.chipEstado, r.estado)
            pintarSeveridad(binding.chipSeveridad, r.severidad)

            // Nombre de transición único: la tarjeta es el elemento compartido que
            // se expande hacia el detalle (container transform).
            ViewCompat.setTransitionName(binding.root, "reporte_${r.ticket}")
            binding.root.setOnClickListener { onClick(binding.root, r) }
        }

        private fun pintarEstado(chip: TextView, estado: EstadoReporte) {
            val ctx = chip.context
            chip.text = estado.etiqueta
            val (bg, color) = when (estado) {
                EstadoReporte.RESUELTO -> R.drawable.bg_chip_permitido to R.color.verde_hoja
                EstadoReporte.EN_PROCESO, EstadoReporte.EN_RUTA -> R.drawable.bg_chip_permitir to R.color.naranja_terracota
                EstadoReporte.RECIBIDO -> R.drawable.bg_chip_azul to R.color.azul
                EstadoReporte.DUPLICADO -> R.drawable.bg_chip_opcional to R.color.texto_secundario
            }
            chip.setBackgroundResource(bg)
            chip.setTextColor(ContextCompat.getColor(ctx, color))
        }

        private fun pintarSeveridad(chip: TextView, severidad: Severidad) {
            val ctx = chip.context
            chip.text = ctx.getString(R.string.reportes_severidad, severidad.name)
            val color = when (severidad) {
                Severidad.ALTA -> R.color.sev_alta
                Severidad.MEDIA -> R.color.sev_media
                Severidad.BAJA -> R.color.sev_baja
            }
            chip.setTextColor(ContextCompat.getColor(ctx, color))
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MiReporte>() {
            override fun areItemsTheSame(a: MiReporte, b: MiReporte) = a.ticket == b.ticket
            override fun areContentsTheSame(a: MiReporte, b: MiReporte) = a == b
        }
    }
}
