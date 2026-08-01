package com.moviles.minkia.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.moviles.minkia.R
import com.moviles.minkia.data.model.PuntoCritico
import com.moviles.minkia.data.model.Severidad
import com.moviles.minkia.databinding.ItemPuntoCriticoBinding

/**
 * Adaptador de la lista de puntos críticos cercanos. Usa ListAdapter + DiffUtil
 * para actualizar solo lo que cambia, y ViewBinding para el acceso a las vistas.
 */
class PuntoCriticoAdapter(
    private val onClick: (PuntoCritico) -> Unit = {}
) : ListAdapter<PuntoCritico, PuntoCriticoAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPuntoCriticoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPuntoCriticoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(punto: PuntoCritico) {
            binding.fotoReporte.cargar(punto.fotoUrl)
            binding.tvDireccion.text = punto.direccion
            binding.tvReferencia.text = binding.root.resources.getQuantityString(
                R.plurals.home_punto_reportes,
                punto.cantidadReportes,
                punto.referencia,
                punto.cantidadReportes
            )
            binding.tvSeveridad.text = when (punto.severidad) {
                Severidad.ALTA -> "ALTA"
                Severidad.MEDIA -> "MEDIA"
                Severidad.BAJA -> "BAJA"
            }
            binding.root.setOnClickListener { onClick(punto) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PuntoCritico>() {
            override fun areItemsTheSame(old: PuntoCritico, new: PuntoCritico) =
                old.id == new.id

            override fun areContentsTheSame(old: PuntoCritico, new: PuntoCritico) =
                old == new
        }
    }
}
