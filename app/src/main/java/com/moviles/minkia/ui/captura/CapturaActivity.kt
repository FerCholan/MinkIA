package com.moviles.minkia.ui.captura

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.moviles.minkia.R
import com.moviles.minkia.core.BaseActivity
import com.moviles.minkia.databinding.ActivityCapturaBinding
import com.moviles.minkia.ui.analisis.AnalisisActivity
import java.io.File

/**
 * Captura con la cámara (mockup C09). Usa CameraX para mostrar el preview en
 * vivo de la cámara trasera y tomar la foto con máxima calidad. Al capturar,
 * guarda la imagen en cache y la entrega a la pantalla de análisis. La IA del
 * análisis corre después (C10); acá solo se obtiene la foto bien enfocada.
 */
class CapturaActivity : BaseActivity<ActivityCapturaBinding>() {

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var flashEncendido = false

    private val pedirCamara =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) iniciarCamara() else sinPermiso()
        }

    override fun inflateBinding(inflater: LayoutInflater) = ActivityCapturaBinding.inflate(inflater)

    override fun onViewReady(savedInstanceState: Bundle?) {
        // Pantalla oscura (preview de cámara): iconos del sistema en claro.
        barrasClaras()
        aplicarInsetsVerticales(binding.controles)
        configurarAcciones()

        if (tieneCamara()) iniciarCamara() else pedirCamara.launch(Manifest.permission.CAMERA)
    }

    private fun configurarAcciones() {
        binding.btnVolver.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener { alternarFlash() }
        binding.btnCapturar.setOnClickListener { tomarFoto() }
        configurarTapToFocus()
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val provider = futuro.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                // Priorizamos calidad sobre velocidad para que la IA analice bien.
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA, // apunta al foco de basura
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.captura_error, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
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

        val archivo = File(cacheDir, "minkia_${System.currentTimeMillis()}.jpg")
        val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()

        captura.takePicture(
            opciones,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                    abrirAnalisis(archivo.absolutePath)
                }

                override fun onError(exc: ImageCaptureException) {
                    binding.btnCapturar.isEnabled = true
                    Toast.makeText(this@CapturaActivity, R.string.captura_error, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun abrirAnalisis(rutaFoto: String) {
        startActivity(
            Intent(this, AnalisisActivity::class.java)
                .putExtra(AnalisisActivity.EXTRA_FOTO, rutaFoto)
        )
        // No finish(): al volver del análisis, el usuario puede tomar otra foto.
        binding.btnCapturar.isEnabled = true
    }

    private fun tieneCamara(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun sinPermiso() {
        Toast.makeText(this, R.string.captura_sin_permiso, Toast.LENGTH_LONG).show()
        finish()
    }
}
