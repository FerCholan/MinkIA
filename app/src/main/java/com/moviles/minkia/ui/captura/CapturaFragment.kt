package com.moviles.minkia.ui.captura

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseFragment
import com.moviles.minkia.core.aplicarInsetsVerticales
import com.moviles.minkia.core.barrasClaras
import com.moviles.minkia.databinding.FragmentCapturaBinding
import com.moviles.minkia.ui.reporte.ReporteFlowViewModel
import java.io.File

/**
 * Captura con la cámara (mockup C09), destino inicial del grafo nav_reporte.
 * Usa CameraX para mostrar el preview en vivo de la cámara trasera y tomar la
 * foto con máxima calidad. Al capturar, guarda la imagen en cache y la
 * entrega al [ReporteFlowViewModel] compartido (fijarFoto): es el único dato
 * que este paso aporta al flujo. La IA del análisis corre después (C10), en
 * la pantalla siguiente; acá solo se obtiene la foto bien enfocada.
 */
class CapturaFragment : BaseFragment<FragmentCapturaBinding>() {

    // Instancia única para los cuatro pasos del flujo (ver KDoc de la clase):
    // navGraphViewModels(R.id.nav_reporte) la ata al NavBackStackEntry del
    // GRAFO, no al de este fragment puntual, así que sobrevive a la
    // navegación hacia Análisis/Formulario/Confirmación y recién se destruye
    // cuando se sale de nav_reporte completo.
    private val flujo: ReporteFlowViewModel by navGraphViewModels(R.id.nav_reporte) {
        ReporteFlowViewModel.Factory(requireContext().applicationContext)
    }

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var flashEncendido = false

    private val pedirCamara =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) iniciarCamara() else sinPermiso()
        }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCapturaBinding.inflate(inflater, container, false)

    override fun onViewReady(savedInstanceState: Bundle?) {
        // Pantalla oscura (preview de cámara): iconos del sistema en claro.
        requireActivity().barrasClaras()
        binding.controles.aplicarInsetsVerticales()
        configurarAcciones()

        if (tieneCamara()) iniciarCamara() else pedirCamara.launch(Manifest.permission.CAMERA)
    }

    /**
     * MainActivity (quien aloja este grafo) no está fija en vertical en el
     * Manifest, a diferencia de la extinta CapturaActivity: el resto del
     * flujo (Formulario, con su mapa) y el resto de la app rotan libre. Solo
     * mientras el preview de la cámara está en pantalla se fuerza portrait
     * acá, en código, y se restaura apenas se abandona este paso (ver
     * onBindingDestroy). Sin esa restauración, salir de Captura dejaría el
     * resto de la app bloqueada en vertical aunque el usuario nunca vuelva a
     * esta pantalla.
     */
    override fun onResume() {
        super.onResume()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun configurarAcciones() {
        binding.btnVolver.setOnClickListener { findNavController().popBackStack() }
        binding.btnFlash.setOnClickListener { alternarFlash() }
        binding.btnCapturar.setOnClickListener { tomarFoto() }
        configurarTapToFocus()
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(requireContext())
        futuro.addListener({
            // La vista pudo destruirse mientras se resolvía el provider (permiso
            // concedido tarde, navegación rápida): sin esta guarda, tocar `binding`
            // acá abajo tira NullPointerException porque BaseFragment ya lo liberó.
            if (view == null) return@addListener
            val provider = futuro.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                // Priorizamos calidad sobre velocidad para que la IA analice bien.
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            try {
                provider.unbindAll()
                // viewLifecycleOwner, NO el fragment ni la Activity: el ciclo de vida
                // que manda acá es el de la VISTA. CameraX desvincula la cámara sola
                // (sin esperar a onBindingDestroy) apenas ese lifecycle llega a
                // DESTROYED, que es exactamente cuando este fragment deja de mostrar
                // el preview (al navegar a Análisis o al salir del flujo). Bindear a
                // "this" (el fragment) llegaría tarde: el fragment puede seguir vivo,
                // sin vista, con la cámara innecesariamente prendida.
                camera = provider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA, // apunta al foco de basura
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.captura_error, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Tocar el preview enfoca y mide la exposición en ese punto (tap-to-focus). */
    private fun configurarTapToFocus() {
        binding.previewView.setOnTouchListener { vista, evento ->
            if (evento.action == MotionEvent.ACTION_UP) {
                val punto = binding.previewView.meteringPointFactory
                    .createPoint(evento.x, evento.y)
                val accion = FocusMeteringAction.Builder(punto).build()
                camera?.cameraControl?.startFocusAndMetering(accion)
                vista.performClick()
            }
            true
        }
    }

    private fun alternarFlash() {
        flashEncendido = !flashEncendido
        imageCapture?.flashMode =
            if (flashEncendido) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        binding.btnFlash.setImageResource(
            if (flashEncendido) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
    }

    private fun tomarFoto() {
        val captura = imageCapture ?: return
        binding.btnCapturar.isEnabled = false

        val archivo = File(requireContext().cacheDir, "minkia_${System.currentTimeMillis()}.jpg")
        val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()

        captura.takePicture(
            opciones,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                    if (view == null) return // la vista pudo destruirse mientras se escribía el archivo
                    abrirAnalisis(archivo.absolutePath)
                }

                override fun onError(exc: ImageCaptureException) {
                    if (view == null) return
                    binding.btnCapturar.isEnabled = true
                    Toast.makeText(requireContext(), R.string.captura_error, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun abrirAnalisis(rutaFoto: String) {
        // Reemplaza el putExtra de antes: la foto queda en el ViewModel
        // compartido: Análisis la lee de ahí (ver AnalisisFragment).
        flujo.fijarFoto(rutaFoto)
        findNavController().navigate(R.id.action_captura_a_analisis)
        // No hay finish(): al volver de Análisis (p. ej. "no se detectó
        // basura, retomar"), este fragment se recrea listo para otra foto.
        binding.btnCapturar.isEnabled = true
    }

    private fun tieneCamara(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun sinPermiso() {
        Toast.makeText(requireContext(), R.string.captura_sin_permiso, Toast.LENGTH_LONG).show()
        findNavController().popBackStack()
    }

    /**
     * BaseFragment sella onDestroyView(); este hook es su reemplazo y corre
     * ANTES de liberar el binding (ver BaseFragment.onBindingDestroy). Acá se
     * deshace lo que onResume/iniciarCamara dejaron pendiente:
     * - Restaura la orientación libre (ver onResume): sin esto, el resto de
     *   la app queda bloqueada en vertical.
     * - Desvincula la cámara de forma explícita. CameraX ya la desvincula
     *   sola al destruirse viewLifecycleOwner (ver iniciarCamara), pero
     *   hacerlo también acá es determinista y no depende únicamente del
     *   orden implícito de los callbacks de lifecycle.
     */
    override fun onBindingDestroy() {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        cameraProvider?.unbindAll()
        cameraProvider = null
        camera = null
        imageCapture = null
    }
}
