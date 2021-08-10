/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.basic.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.rotationMatrix
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.computeExifOrientation
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera2.basic.CameraActivity
import com.example.android.camera2.basic.R
import com.example.calibrarlongituddeonda.Autorotar
import com.example.calibrarlongituddeonda.Regresion
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeoutException
import java.util.Date
import java.util.Locale
import kotlin.RuntimeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.pow

class CameraFragment : Fragment() {

    //Defino cosillas
    var x = 0
    val interval = 3_000L
    var previousMillis = 0L
    val n = 21
    var h = FloatArray(3)

    var intensidad = mutableListOf<Double>()
    var tiempos = mutableListOf<Double>()
    var intensidadTodos = mutableListOf<Double>()
    var tiemposTodos = mutableListOf<Double>()

    lateinit var m : DoubleArray
    lateinit var matrix : Matrix
    var alto = 1
    var halto = 1
    lateinit var bitmapRotado : Bitmap
    var intens = 0
    var aargb = 0



    /** AndroidX navigation arguments */
    private val args: CameraFragmentArgs by navArgs()


    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    /** Readers used as buffers for camera still shots */
    private lateinit var imageReader: ImageReader

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen */
    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            overlay.background = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            overlay.postDelayed({
                // Remove white flash animation
                overlay.background = null
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    /** [HandlerThread] where all buffer reading operations run */
    private val imageReaderThread = HandlerThread("imageReaderThread").apply { start() }

    /** [Handler] corresponding to [imageReaderThread] */
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    /** Where the camera preview is displayed */
    private lateinit var viewFinder: AutoFitSurfaceView

    /** Overlay on top of the camera preview */
    private lateinit var overlay: View

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Internal reference to the ongoing [CameraCaptureSession] configured with our parameters */
    private lateinit var session: CameraCaptureSession

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData



    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        overlay = view.findViewById(R.id.overlay)
        viewFinder = view.findViewById(R.id.view_finder)

        viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {

                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(
                        viewFinder.display, characteristics, SurfaceHolder::class.java)
                Log.d(TAG, "View finder size: ${viewFinder.width} x ${viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                viewFinder.setAspectRatio(previewSize.width, previewSize.height)

                // To ensure that size is set, initialize camera in the view's thread
                view.post { initializeCamera() }
            }
        })

        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer {
                orientation -> Log.d(TAG, "Orientation changed: $orientation")
            })
        }
        btn_mostrar.setOnClickListener(
                {
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                            .navigate(CameraFragmentDirections.actionCameraFragmentToLongOndaFragment(args.cameraId, args.pixelFormat, args.tita, args.b, h))

                }
        )
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating capture request
     * - Sets up the still image capture listeners
     */
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        // Open the selected camera
        camera = openCamera(cameraManager, args.cameraId, cameraHandler)

        // Initialize an image reader which will be used to capture still photos
        val size = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(args.pixelFormat).maxBy { it.height * it.width }!!
        imageReader = ImageReader.newInstance(
                size.width, size.height, args.pixelFormat, IMAGE_BUFFER_SIZE)

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(viewFinder.holder.surface, imageReader.surface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        val captureRequest = camera.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(viewFinder.holder.surface) }

        // This will keep sending the capture request as frequently as possible until the
        // session is torn down or session.stopRepeating() is called
        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)

        // Listen to the capture button
        capture_button.setOnClickListener {

            // Disable click listener to prevent multiple requests simultaneously in flight
            it.isEnabled = false

            //Acá le meto un loopazo para que saque varias fotos
            x = -1
            while (x<n-1) {
                if (System.currentTimeMillis()-previousMillis >= interval){ //le doy un intervalo de pausa
                    print("entraste")
                    previousMillis = System.currentTimeMillis()
                    x++
                    println("x=$x")

                    // Perform I/O heavy operations in a different scope
                    lifecycleScope.launch(Dispatchers.IO) {
                        takePhoto().use { result ->
                            Log.d(TAG, "Result received: $result")

                            // Save the result to disk
                            val output = saveResult(result)
                            Log.d(TAG, "Image saved: ${output.absolutePath}")

                            /**
                            // If the result is a JPEG file, update EXIF metadata with orientation info
                            if (output.extension == "jpg") {
                            val exif = ExifInterface(output.absolutePath)
                            exif.setAttribute(
                            ExifInterface.TAG_ORIENTATION, result.orientation.toString())
                            exif.saveAttributes()
                            Log.d(TAG, "EXIF metadata saved: ${output.absolutePath}")
                            }

                            // Display the photo taken to user
                            lifecycleScope.launch(Dispatchers.Main) {
                            navController.navigate(CameraFragmentDirections
                            .actionCameraToJpegViewer(output.absolutePath)
                            .setOrientation(result.orientation)
                            .setDepth(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            result.format == ImageFormat.DEPTH_JPEG))
                            }*/
                        }

                        // Re-enable click listener after photo is taken
                        it.post { it.isEnabled = true }
                    }



                }
            }

            println(tiemposTodos)
            println(intensidadTodos)
            val regresion = Regresion()
            val p = regresion.getPolynomialFitter(tiempos, intensidad,1)
            val Kas = mutableListOf<Double>()
            val nuevasIntens = mutableListOf<Double>()
            println("\n El lineal da= %f, %f".format(p[0],p[1]))

            for (i in 0 until tiemposTodos.size) {
                if (intensidadTodos[i] > 150){
                    Kas.add((tiemposTodos[i]*p[1]+p[0])/intensidadTodos[i])
                    nuevasIntens.add(intensidadTodos[i])
                }
            }
            println(Kas)
            var hDouble = regresion.getPolynomialFitter(nuevasIntens, Kas, 2)
            h[0] = (hDouble[0]).toFloat()
            h[1] = (hDouble[1]).toFloat()
            h[2] = (hDouble[2]).toFloat()
            println("Ya está calibrado(?)")
            println("\nEl cuadratico da=  %f, %f, %f".format(h[0],h[1], h[2]*1e5))


        }
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
            manager: CameraManager,
            cameraId: String,
            handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }
            override fun onError(device: CameraDevice, error: Int) {
                val msg = when(error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Starts a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine
     */
    private suspend fun createCaptureSession(
            device: CameraDevice,
            targets: List<Surface>,
            handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Create a capture session using the predefined targets; this also involves defining the
        // session state callback to be notified of when the session is ready
        device.createCaptureSession(targets, object: CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    //Inicializo unas var
    var exposureTime = 100_000_000L
    private val frac : Long = 30_000_000L

    /**
     * Helper function used to capture a still image using the [CameraDevice.TEMPLATE_STILL_CAPTURE]
     * template. It performs synchronization between the [CaptureResult] and the [Image] resulting
     * from the single capture, ancd outputs a [CombinedCaptureResult] object.
     */
    private suspend fun takePhoto():
            CombinedCaptureResult = suspendCoroutine { cont ->

        // Flush any images left in the image reader
        @Suppress("ControlFlowWithEmptyBody")
        while (imageReader.acquireNextImage() != null) {}

        // Start a new image queue
        val imageQueue = ArrayBlockingQueue<Image>(IMAGE_BUFFER_SIZE)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireNextImage()
            Log.d(TAG, "Image available in queue: ${image.timestamp}")
            imageQueue.add(image)
        }, imageReaderHandler)

        val captureRequest = session.device.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE).apply { addTarget(imageReader.surface) }


        //Invento mio

        //Leo el rango de texp posible y defino los limites
        val rangoS = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val tmin= rangoS?.lower
        val tmax = rangoS?.upper
        var t = tmin?.let { tmax?.minus(it) }?.div(n) //Es redundante ahora, pero en algunos casos sirve
        //val captureBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)

        //captureBuilder.addTarget(surface!!)
        captureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        //captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)

        //Defino los texp que usaré
        if (x < n/2) {
            t = tmin?.let { frac.minus(it) }?.div(n/2)
            if (tmin != null && t != null) {
                exposureTime = tmin+ t *x
            }
        } else if (x < n ) {
            t = frac.let { tmax?.minus(it) }?.div(n/2)
            if (tmin != null && t != null) {
                exposureTime = frac+ t *(x-n/2)
            }
        }

        //Lo seteo
        captureRequest.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime) // en nanoSec
        captureRequest.set(CaptureRequest.SENSOR_SENSITIVITY, 100) //algo fijo
        println("texp= $exposureTime")
        //captureBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration)

        //ajustar AWB y AF al modo OFF
        captureRequest.set(CaptureRequest.CONTROL_AWB_LOCK, false )
        captureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)


        //ya saca esa maldita foto
        session.capture(captureRequest.build(), object : CameraCaptureSession.CaptureCallback() {

            override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
                viewFinder.post(animationTask)
            }

            override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)
                val resultTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP)
                Log.d(TAG, "Capture result received: $resultTimestamp")

                // Set a timeout in case image captured is dropped from the pipeline
                val exc = TimeoutException("Image dequeuing took too long")
                val timeoutRunnable = Runnable { cont.resumeWithException(exc) }
                imageReaderHandler.postDelayed(timeoutRunnable, IMAGE_CAPTURE_TIMEOUT_MILLIS)

                // Loop in the coroutine's context until an image with matching timestamp comes
                // We need to launch the coroutine context again because the callback is done in
                //  the handler provided to the `capture` method, not in our coroutine context
                @Suppress("BlockingMethodInNonBlockingContext")
                lifecycleScope.launch(cont.context) {
                    while (true) {

                        // Dequeue images while timestamps don't match
                        val image = imageQueue.take()
                        // TODO(owahltinez): b/142011420
                        // if (image.timestamp != resultTimestamp) continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                image.format != ImageFormat.DEPTH_JPEG &&
                                image.timestamp != resultTimestamp) continue
                        Log.d(TAG, "Matching image dequeued: ${image.timestamp}")

                        // Unset the image reader listener
                        imageReaderHandler.removeCallbacks(timeoutRunnable)
                        imageReader.setOnImageAvailableListener(null, null)

                        // Clear the queue of images, if there are left
                        while (imageQueue.size > 0) {
                            imageQueue.take().close()
                        }

                        // Compute EXIF orientation metadata
                        val rotation = relativeOrientation.value ?: 0
                        val mirrored = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                                CameraCharacteristics.LENS_FACING_FRONT
                        val exifOrientation = computeExifOrientation(rotation, mirrored)

                        // Build the result and resume progress
                        cont.resume(CombinedCaptureResult(
                                image, result, exifOrientation, imageReader.imageFormat))

                        // There is no need to break out of the loop, this coroutine will suspend
                    }
                }
            }
        }, cameraHandler)
    }

    /** Helper function used to save a [CombinedCaptureResult] into a [File] */
    private suspend fun saveResult(result: CombinedCaptureResult): File = suspendCoroutine { cont ->
        when (result.format) {

            // When the format is JPEG or DEPTH JPEG we can simply save the bytes as-is
            ImageFormat.JPEG, ImageFormat.DEPTH_JPEG -> {
                val buffer = result.image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).apply { buffer.get(this) }
                //Roto
                val myFirstBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                var matrix = rotationMatrix(90f) //las matrices rotan bitmaps
                //val tita = AutoRotateFragment().tita //uso el tita ya calculado
                //val m = AutoRotateFragment().m2 //re same
                val myBitmap = Bitmap.createBitmap(myFirstBitmap, 0, 2*myFirstBitmap.height/5, 4*myFirstBitmap.width/5, myFirstBitmap.height/5, matrix, true)
                val tita = args.tita
                val b= args.b
                matrix = rotationMatrix(tita)
                bitmapRotado = Bitmap.createBitmap(myBitmap, 0, 0, myBitmap.width, myBitmap.height, matrix, true) //creo un bitmap pero rotado
                alto = bitmapRotado.height
                halto = alto.div(2.0).toInt() //No me decido que poner todavia
                var valor = 0
                var nosecontar = 0
                for (j in b.toInt()-5..b.toInt()+5) {
                    for (i in 2*alto.div(3) until alto) {
                        aargb = bitmapRotado.getPixel(j, i)
                        valor += ((Color.red(aargb) + Color.blue(aargb) + Color.green(aargb)))
                        nosecontar++
                    }
                }
                valor = valor.div(nosecontar)
                println("Intens=${valor}")
                intens = valor
                //Entre 2 y 89 se comporta bastante lineal, esos datos usos para el ajuste
                if (intens in 10..150){
                    intensidad.add(intens.toDouble())
                    tiempos.add((exposureTime*1e-5))
                }
                intensidadTodos.add(intens.toDouble())
                tiemposTodos.add((exposureTime*1e-5))
                try {
                    val output = createFile(requireContext(), "jpg")
                    FileOutputStream(output).use { it.write(bytes) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write JPEG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // When the format is RAW we use the DngCreator utility library
            ImageFormat.RAW_SENSOR -> {
                val dngCreator = DngCreator(characteristics, result.metadata)
                try {
                    val output = createFile(requireContext(), "dng")
                    FileOutputStream(output).use { dngCreator.writeImage(it, result.image) }
                    cont.resume(output)
                } catch (exc: IOException) {
                    Log.e(TAG, "Unable to write DNG image to file", exc)
                    cont.resumeWithException(exc)
                }
            }

            // No other formats are supported by this sample
            else -> {
                val exc = RuntimeException("Unknown image format: ${result.image.format}")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }
    }


    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        imageReaderThread.quitSafely()
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName

        /** Maximum number of images that will be held in the reader's buffer */
        private const val IMAGE_BUFFER_SIZE: Int = 3

        /** Maximum time allowed to wait for the result of an image capture */
        private const val IMAGE_CAPTURE_TIMEOUT_MILLIS: Long = 5000

        /** Helper data class used to hold capture metadata with their associated image */
        data class CombinedCaptureResult(
                val image: Image,
                val metadata: CaptureResult,
                val orientation: Int,
                val format: Int
        ) : Closeable {
            override fun close() = image.close()
        }

        /**
         * Create a [File] named a using formatted timestamp with the current date and time.
         *
         * @return [File] created.
         */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "IMG_${sdf.format(Date())}.$extension")
        }
    }
}