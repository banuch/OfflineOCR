package com.app.offlineocr

import android.Manifest
import android.R.attr.bitmap
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import com.app.offlineocr.databinding.ActivityCameraBinding
import com.app.offlineocr.databinding.ActivityMainBinding
import com.app.offlineocr.databinding.LayoutCameraBinding
import com.bumptech.glide.Glide
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var binding2:  ActivityCameraBinding
    private var imageFile: File? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraBinding: LayoutCameraBinding


    private val TAG = "Camera View"
    private var imgFileName="default.jpg"

    private var image_uri: Uri? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding2=ActivityCameraBinding.inflate(layoutInflater)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding2.root)

        cameraBinding = binding2.cameraView

        binding2.btnOk.setOnClickListener {
            binding2.cameraView.root.visibility = View.VISIBLE
            binding2.image.visibility = View.GONE
            binding2.borderView.visibility = View.VISIBLE
        }
        binding2.btnCancel.setOnClickListener {

            // Pass the bitmap to the next activity
            val intent = Intent(this, MainActivity::class.java)

            Log.d(TAG, "clicked back save file name: $imgFileName")

            intent.putExtra("filename", imgFileName)
            startActivity(intent)
        }


        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
        cameraBinding = binding2.cameraView
      binding2.cameraCaptureButton.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()


    }

    // Function to convert ImageView to Bitmap
    fun getBitmapFromImageView(imageView: ImageView): Bitmap {
        // Step 1: Get the Drawable from ImageView
        val drawable: Drawable = imageView.drawable

        // Step 2: Convert the Drawable to Bitmap
        val bitmap: Bitmap = when {
            drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0 -> {
                // Handle invalid drawable
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
            else -> {
                Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
            }
        }

        // Create a canvas and draw the Drawable onto the Bitmap
        bitmap.apply {
            val canvas = android.graphics.Canvas(this)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }

        return bitmap
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        imgFileName=SimpleDateFormat(
            FILENAME_FORMAT, Locale.US
        ).format(System.currentTimeMillis()) + ".jpg"


        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            imgFileName
        )

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    onImageCaptured(savedUri)
                    val msg = "Photo capture succeeded: $savedUri"
                   // imgFileName=savedUri.toString()
                //    Log.d(TAG, "fILE PATH: $imgFileName")
                    image_uri=savedUri
                   // Toast.makeText(this@CameraActivity, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                }
            })
    }





    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            this, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull().let {
            File(
                it,
                resources.getString(R.string.app_name)
            ).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(cameraBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()



            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val viewPort = ViewPort.Builder(Rational(350, 100), Surface.ROTATION_0).build()
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture!!)
                .setViewPort(viewPort)
                .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, useCaseGroup
                )
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                // finish()
            }
            return
        }
    }


    companion object {
        private const val TAG = "AddTaskDialog"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    }

    private fun onQrDetected(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun onImageCaptured(uri: Uri) {
        val file = File(uri.path!!)


        //imgFileName=uri.path.toString()

        Glide.with(binding2.image).load(file).into(binding2.image)
        showImage()


    }


    private fun showImage() {
        binding2.borderView.visibility = View.GONE
        binding2.cameraView.root.visibility = View.GONE
        binding2.image.visibility = View.VISIBLE
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }



}