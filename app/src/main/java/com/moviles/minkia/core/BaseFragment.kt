package com.moviles.minkia.core

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * Fragment base con manejo seguro de ViewBinding. Cada fragment concreto solo
 * provee cómo inflar su binding y qué hacer en [onViewReady]; el ciclo de vida
 * del binding (crearlo y liberarlo en onDestroyView para no filtrar la vista)
 * queda resuelto acá una sola vez.
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    protected val binding get() = _binding!!

    /** Cómo inflar el binding concreto, p. ej. FragmentHomeBinding::inflate. */
    abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    /** Punto de entrada de la vista: equivalente a onViewCreated, ya con binding. */
    abstract fun onViewReady(savedInstanceState: Bundle?)

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBindingDestroy()
        _binding = null
    }

    /** Hook opcional para limpiar referencias (p. ej. adapters del RecyclerView). */
    protected open fun onBindingDestroy() {}
}
