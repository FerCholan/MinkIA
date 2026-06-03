package com.moviles.minkia.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.moviles.minkia.databinding.ItemOnboardingBinding

/**
 * Adaptador del ViewPager2 del onboarding. Pinta cada [OnboardingPage] con su
 * ícono, título y descripción.
 */
class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOnboardingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = pages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    inner class ViewHolder(
        private val binding: ItemOnboardingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(page: OnboardingPage) {
            binding.ivIcono.setImageResource(page.icono)
            binding.tvTitulo.setText(page.titulo)
            binding.tvDescripcion.setText(page.descripcion)
        }
    }
}
