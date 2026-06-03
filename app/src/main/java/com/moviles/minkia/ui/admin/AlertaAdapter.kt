package com.moviles.minkia.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.moviles.minkia.R
import com.moviles.minkia.data.model.AlertaAdmin
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.ItemAlertaBinding

/** Adaptador de la bandeja de alertas (A02). */
class AlertaAdapter(
    private val onClick: (AlertaAdmin) -> Unit
) : ListAdapter<AlertaAdmin, AlertaAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlertaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(
        private val binding: ItemAlertaBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(a: AlertaAdmin) {
            val ctx = binding.root.context
            binding.tvDireccion.text = a.direccion
            binding.tvTiempo.text = a.tiempoTexto

            binding.chipNuevo.visibility = if (a.nuevo) View.VISIBLE else View.GONE

            binding.chipSeveridad.text = a.severidad.name
            binding.chipSeveridad.setTextColor(ContextCompat.getColor(ctx, colorSev(a.severidad)))

            if (a.agrupados > 1) {
                binding.chipGrupo.visibility = View.VISIBLE
                binding.chipGrupo.text = ctx.getString(R.string.admin_alertas_agrupados, a.agrupados)
            } else {
                binding.chipGrupo.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(a) }
        }

        private fun colorSev(s: Severidad) = when (s) {
            Severidad.ALTA -> R.color.sev_alta
            Severidad.MEDIA -> R.color.sev_media
            Severidad.BAJA -> R.color.sev_baja
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AlertaAdmin>() {
            override fun areItemsTheSame(a: AlertaAdmin, b: AlertaAdmin) = a.direccion == b.direccion
            override fun areContentsTheSame(a: AlertaAdmin, b: AlertaAdmin) = a == b
        }
    }
}
