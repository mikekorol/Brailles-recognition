package com.example.brailles

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.brailles.recognition.InfoActivity
import com.example.brailles.recognition.R
import kotlinx.android.synthetic.main.activity_model.*
import java.io.File
import java.io.IOException
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

val PREDICTION_RESULT: AtomicReference<String?> = AtomicReference()

class ModelActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var ImagePath: String
    private lateinit var ImageFile: File
    private lateinit var ImageBit: Bitmap

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    val es: ExecutorService = Executors.newSingleThreadExecutor()
    val canContinue = AtomicBoolean(true)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @Throws(IOException::class)
    private fun readBytes(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }!!

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(
                    FILENAME_FORMAT, Locale.US
                ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo capture succeeded: $savedUri"

                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                Log.d(TAG, msg)

                ImageFile = photoFile
                ImagePath = savedUri.path.toString()

                val imageBytes: ByteArray = readBytes(applicationContext, savedUri) // context - ?

                doInBackground(imageBytes)
            }
        })
    }

    // NOTE: call from UI thread
    fun doInBackground(image: ByteArray) {
        if (canContinue.get()) {
            es.submit(Runnable {
                if (canContinue.compareAndSet(true, false)) {
                    try {
                        val result = imageToServer(image) // this never runs multiple instances at once
                        runOnUiThread {
                            PREDICTION_RESULT.set(result)
                            val intentInfo = Intent(this, InfoActivity::class.java)
                            startActivityForResult(intentInfo, 1)
                        }
                    } finally {
                        canContinue.set(true)
                    }
                }
            })
        }
    }

    // writes string as bytes, fills the rest of the resulting array with 0-byte value
    fun convertStringToConstByteArray(s: String, arraySize: Int): ByteArray {
        val bytes = s.encodeToByteArray()
        val bytesList = bytes.asList().toMutableList()
        bytesList.addAll(MutableList(arraySize - bytes.size) { 0 })
        return bytesList.toByteArray()
    }

    fun toBytes(i: Int): ByteArray {
        val result = ByteArray(4)
        result[0] = (i shr 24).toByte()
        result[1] = (i shr 16).toByte()
        result[2] = (i shr 8).toByte()
        result[3] = i /*>> 0*/.toByte()
        return result
    }

    fun imageToServer(imageBytes: ByteArray): String {
        val imageBytesSize = imageBytes.size
        val port = 5067

        println("SIZE: $imageBytesSize")
        println("connecting...")

        Socket("192.168.1.6", port).use {
            println("OK connected, writing...")
            val bytes = toBytes(imageBytesSize)

            println("bytes = ${bytes.toList()}")
            it.getOutputStream().use {
                it.write(bytes)
                it.write(imageBytes)
            }

            println("data sent")

            it.close()

        }

        Socket("192.168.1.6", port).use {
            println("OK connected, waiting for server answering...")
            var answer = ""
            try {
                it.getInputStream().use {
                    println("OK reading answer...")
                    val input = it.readBytes()
                    answer = String(input)
                }
            } catch (e: IOException) {
                println("Can't get input stream")
            }

            println("OK result received: $answer")
            it.close()
            return answer
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}