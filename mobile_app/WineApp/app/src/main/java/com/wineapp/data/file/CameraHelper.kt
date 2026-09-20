package com.wineapp.data.file

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHelper @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    var previewView: PreviewView? = null
        private set
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraSelector: androidx.camera.core.CameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var pendingBind = false

    fun bindToLifecycle(
        owner: LifecycleOwner,
        preview: PreviewView,
        selector: androidx.camera.core.CameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
    ) {
        this.previewView = preview
        this.lifecycleOwner = owner
        this.cameraSelector = selector

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingBind = true
            return
        }
        doBind()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted && pendingBind) {
            pendingBind = false
            doBind()
        }
    }

    private fun doBind() {
        val owner = lifecycleOwner ?: return
        val preview = previewView ?: return

        val surfaceProvider = preview.surfaceProvider
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val previewUseCase = androidx.camera.core.Preview.Builder().build().also {
                it.surfaceProvider = surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build()

            cameraProvider.bindToLifecycle(owner, cameraSelector, *arrayOf(previewUseCase, imageCapture!!))
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePicture(onSuccess: (File) -> Unit, onError: (Exception) -> Unit) {
        val imageCapture = this.imageCapture ?: return
        val photoFile = createImageFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSuccess(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(Exception(exception.message, exception))
            }
        })
    }

    fun toggleFlash(): Int {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        return flashMode
    }

    fun getFlashMode(): Int = flashMode

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir("images")!!
        return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
