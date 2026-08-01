package com.moviles.minkia.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.moviles.minkia.R
import com.moviles.minkia.data.model.FilaReporte
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.ItemReporteFilaBinding

/**
 * Adaptador del reporte municipal (A06): una fila por reporte con ticket, nivel,
 * dirección y meta (fecha/hora · estado). Solo lectura, es reportería.
 */
class FilaReporteAdapter : ListAdapter<FilaReporte, FilaReporteAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReporteFilaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: ItemReporteFilaBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(r: FilaReporte) {
            val ctx = binding.root.context
            binding.tvTicket.text = r.ticket.ifBlank { "#MK" }
            binding.tvSeveridad.text = r.severidad.name
            binding.tvSeveridad.setTextColor(ContextCompat.getColor(ctx, colorSev(r.severidad)))
            binding.tvDireccion.text = r.direccion.ifBlank { "Chimbote, Áncash" }
            binding.tvCoords.text = ctx.getString(R.string.admin_val_coord_fmt, r.latitud, r.longitud)
            binding.tvMeta.text = ctx.getString(
                R.string.admin_rep_fila_sub, r.fechaHoraTexto, r.estado.etiqueta, r.tipo.ifBlank { "Basura" }
            )
        }

        private fun colorSev(s: Severidad) = when (s) {
            Severidad.ALTA -> R.color.sev_alta
            Severidad.MEDIA -> R.color.sev_media
            Severidad.BAJA -> R.color.sev_baja
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FilaReporte>() {
            // Por id, no por ticket: el ticket puede repetirse entre reportes.
            override fun areItemsTheSame(a: FilaReporte, b: FilaReporte) = a.id == b.id
            override fun areContentsTheSame(a: FilaReporte, b: FilaReporte) = a == b
        }
    }
}
