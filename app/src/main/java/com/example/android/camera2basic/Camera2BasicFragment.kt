/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2basic

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodec.INFO_TRY_AGAIN_LATER
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import com.example.android.camera2basic.rtmp.FLvMetaData
import com.example.android.camera2basic.rtmp.Packager
import com.example.android.camera2basic.rtmp.RtmpClient
import com.yuliyang.testlibyuv.R
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.experimental.and

class Camera2BasicFragment : Fragment(), View.OnClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    }

    /**
     * ID of the current [CameraDevice].
     */
    private lateinit var cameraId: String

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var textureView: AutoFitTextureView

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var captureSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice].
     */
    private var cameraDevice: CameraDevice? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    private lateinit var streamSize: Size

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@Camera2BasicFragment.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice.close()
            this@Camera2BasicFragment.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            this@Camera2BasicFragment.activity?.finish()
        }

    }

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * An [ImageReader] that handles still image capture.
     */
    private var imageReader: ImageReader? = null

    /**
     * This is the output file for our picture.
     */
    private lateinit var file: File
    private lateinit var flvTmpfile: File

    private lateinit var testVideoFile: File
    private lateinit var op: FileOutputStream

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private val onImageAvailableListener = ImageReader.OnImageAvailableListener {
        //        backgroundHandler?.post(ImageSaver(it.acquireNextImage(), file))

        with(it) {
            val image = acquireLatestImage()
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()

//            val datas = ByteArray(ySize + uSize)
//            yBuffer.get(datas, 0, ySize)//Y
//            for (i in 0..(uSize / 2)) {
//                datas[ySize + i] = uBuffer[i * 2]//U
//            }
//            for (i in 0 until (uSize / 2)) {
//                datas[ySize + uSize / 2 + 1 + i] = uBuffer[i * 2 + 1]//V
//            }

            val Ydatas = ByteArray(ySize)
            yBuffer.get(Ydatas, 0, ySize)//Y
            val UVdatas = ByteArray(uSize)
            uBuffer.get(UVdatas, 0, uSize)//Y
            val outYDatas = ByteArray(ySize)
            val outUDatas = ByteArray(ySize / 4)
            val outVDatas = ByteArray(ySize / 4)

            //旋转90
            YuvUtil.rotateYUV(
                Ydatas,
                image.planes[0].rowStride,
                UVdatas,
                image.planes[1].rowStride,
                outYDatas,
                streamSize.height,
                outUDatas,
                streamSize.height / 2,
                outVDatas,
                streamSize.height / 2,
                streamSize.width,
                streamSize.height,
                90
            )
            val resultArray = ByteArray(outYDatas.size + outUDatas.size + outVDatas.size)
            System.arraycopy(outYDatas, 0, resultArray, 0, outYDatas.size)
            System.arraycopy(outUDatas, 0, resultArray, outYDatas.size, outUDatas.size)
            System.arraycopy(outVDatas, 0, resultArray, outYDatas.size + outUDatas.size, outVDatas.size)
            encoderYUV420(resultArray)
            image.close()
        }
    }

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    /**
     * [CaptureRequest] generated by [.previewRequestBuilder]
     */
    private lateinit var previewRequest: CaptureRequest

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var flashSupported = false

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0

    var rtmpId: Long = -1
    var flvId: Long = -1


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera2_basic, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.picture).setOnClickListener(this)
        view.findViewById<View>(R.id.info).setOnClickListener(this)
        textureView = view.findViewById(R.id.texture)
        testVideoFile = File(activity!!.getExternalFilesDir(null), "test.264")
        if (!testVideoFile.exists()) {
            testVideoFile.createNewFile()
        }
        op = FileOutputStream(testVideoFile)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        file = File(activity!!.getExternalFilesDir(null), PIC_FILE_NAME)
        flvTmpfile = File(activity!!.getExternalFilesDir(null), "flvTemp.flv")
        if (!flvTmpfile.exists()) {
            flvTmpfile.createNewFile()
        }
        rtmpId = RtmpClient.open("rtmp://192.168.2.200/videotest", true)
        flvId = RtmpClient.flvInit(flvTmpfile.absolutePath)
        //发送MateData
        val metadata = FLvMetaData()

        RtmpClient.writeMetadata(rtmpId, metadata.metaData, metadata.metaData.size, System.currentTimeMillis(), 0x12)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs() {
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (cameraDirection != null &&
                    cameraDirection == CameraCharacteristics.LENS_FACING_FRONT
                ) {
                    continue
                }

                val map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                ) ?: continue

                val supportSise = map.getOutputSizes(ImageFormat.YUV_420_888)

                val aspectRatioSize = supportSise.filter {
                    activity!!.screenWidth * it.width == activity!!.screenHeight * it.height
                }
                //选取中间的尺寸
                streamSize = aspectRatioSize[aspectRatioSize.size / 2]
                println("streamSize  $streamSize")

                // For still image captures, we use the largest available size.
                val largest = Collections.min(
                    Arrays.asList(*map.getOutputSizes(ImageFormat.JPEG)),
                    CompareSizesByArea()
                )
                imageReader = ImageReader.newInstance(
                    streamSize.width, streamSize.height,
                    ImageFormat.YUV_420_888, /*maxImages*/ 1
                ).apply {
                    setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
                }

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

                previewSize = chooseOptimalSize(
                    supportSise,
                    activity!!.screenWidth, activity!!.screenHeight
                )

                println("previceSize ${previewSize.width} ${previewSize.height}")

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.width, previewSize.height)
                } else {
                    textureView.setAspectRatio(previewSize.height, previewSize.width)
                }

                // Check if the flash is supported.
                flashSupported =
                        characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                this.cameraId = cameraId

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras.
                return
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                .show(childFragmentManager, FRAGMENT_DIALOG)
        }

    }

    private lateinit var codec: MediaCodec
    private val MIME = "video/avc"

    private fun initMediaCodec() {
        try {
            codec = MediaCodec.createEncoderByType(MIME)

            val format = MediaFormat.createVideoFormat(MIME, streamSize.height, streamSize.width)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 3500 * 1000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SwitchIntDef")
    fun encoderYUV420(input: ByteArray) {
        try {
            val inputBufferIndex = codec.dequeueInputBuffer(5000);
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.apply {
                    clear()
                    put(input)
                    codec.queueInputBuffer(inputBufferIndex, 0, input.size, System.currentTimeMillis(), 0)
                }
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 5000)
            while (true) {
                if (outputBufferIndex < 0 && outputBufferIndex != MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    println(outputBufferIndex)
                    break
                }
                when (outputBufferIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val _AVCDecoderConfigurationRecord =
                            Packager.H264Packager.generateAVCDecoderConfigurationRecord(codec.outputFormat)
                        val packetLen =
                            Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH + _AVCDecoderConfigurationRecord.size
                        val finalBuff = ByteArray(packetLen)
                        Packager.FLVPackager.fillFlvVideoTag(
                            finalBuff,
                            0,
                            true,
                            true,
                            _AVCDecoderConfigurationRecord.size
                        )
                        System.arraycopy(
                            _AVCDecoderConfigurationRecord,
                            0,
                            finalBuff,
                            Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH,
                            _AVCDecoderConfigurationRecord.size
                        )
                        RtmpClient.write264(
                            rtmpId,
                            finalBuff,
                            finalBuff.size,
                            System.currentTimeMillis(),
                            0x09
                        )
                    }
                    else -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        outputBuffer?.apply {
                            outputBuffer.position(bufferInfo.offset + 4)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val realDataLength = outputBuffer.remaining()
                            val outData = ByteArray(realDataLength)
                            op.write(outData)
                            //发送数据
                            val packetLen = Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH +
                                    Packager.FLVPackager.NALU_HEADER_LENGTH +
                                    realDataLength
                            val finalBuff = ByteArray(packetLen)
                            outputBuffer.get(
                                finalBuff,
                                Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH + Packager.FLVPackager.NALU_HEADER_LENGTH,
                                realDataLength
                            )
                            val frameType =
                                finalBuff[Packager.FLVPackager.FLV_VIDEO_TAG_LENGTH + Packager.FLVPackager.NALU_HEADER_LENGTH] and 0x1F
                            Packager.FLVPackager.fillFlvVideoTag(
                                finalBuff,
                                0,
                                false,
                                frameType == 5.toByte(),
                                realDataLength
                            )

                            RtmpClient.write264(
                                rtmpId,
                                finalBuff,
                                finalBuff.size,
                                System.currentTimeMillis(),
                                0x09
                            )
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                        }
                    }
                }
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 5000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseMediaCodec() {
        codec.stop()
        codec.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaCodec()
    }

    /**
     * Opens the camera specified by [Camera2BasicFragment.cameraId].
     */
    private fun openCamera(width: Int, height: Int) {
        val permission = ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission()
            return
        }
        setUpCameraOutputs()
        initMediaCodec()
        configureTransform(width, height)
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }

    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder.addTarget(surface)
            previewRequestBuilder.addTarget(imageReader!!.surface)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice?.createCaptureSession(
                Arrays.asList(surface, imageReader?.surface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (cameraDevice == null) return

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            previewRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // Flash is automatically enabled when necessary.
                            setAutoFlash(previewRequestBuilder)

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder.build()
                            captureSession?.setRepeatingRequest(
                                previewRequest,
                                null, backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, e.toString())
                        }

                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        activity!!.showToast("Failed")
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        activity ?: return
        val rotation = activity!!.windowManager.defaultDisplay.rotation
        println(rotation)
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
//        try {
//            // This is how to tell the camera to lock focus.
//            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
//                    CameraMetadata.CONTROL_AF_TRIGGER_START)
//            // Tell #captureCallback to wait for the lock.
//            state = STATE_WAITING_LOCK
//            captureSession?.capture(previewRequestBuilder.build(), captureCallback,
//                    backgroundHandler)
//        } catch (e: CameraAccessException) {
//            Log.e(TAG, e.toString())
//        }
        captureStillPicture()

    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.captureCallback] from both [.lockFocus].
     */
    private fun captureStillPicture() {
        try {
            if (activity == null || cameraDevice == null) return
            val rotation = activity!!.windowManager.defaultDisplay.rotation

            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE
            )?.apply {
                addTarget(imageReader!!.surface)

                // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
                // We have to take that into account and rotate JPEG properly.
                // For devices with orientation of 90, we return our mapping from ORIENTATIONS.
                // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
                set(
                    CaptureRequest.JPEG_ORIENTATION,
                    (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360
                )

                // Use the same AE and AF modes as the preview.
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
            }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    Log.d(TAG, file.toString())
                    unlockFocus()
                }
            }
            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                Handler(Looper.getMainLooper()).postDelayed({
                    capture(captureBuilder!!.build(), captureCallback, backgroundHandler)
                }, 100)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.picture -> lockFocus()
            R.id.info -> {
                if (activity != null) {
                    AlertDialog.Builder(activity)
                        .setMessage(R.string.intro_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private fun unlockFocus() {
        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            // After this, the camera will go back to the normal state of preview.
            captureSession?.setRepeatingRequest(
                previewRequest, null,
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    private fun setAutoFlash(requestBuilder: CaptureRequest.Builder) {
        if (flashSupported) {
            requestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
        }
    }

    companion object {

        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()
        private val FRAGMENT_DIALOG = "dialog"

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        /**
         * Tag for the [Log].
         */
        private val TAG = "Camera2BasicFragment"

        @JvmStatic
        private fun chooseOptimalSize(
            choices: Array<Size>,
            screenWidth: Int,
            screenHeight: Int
        ): Size {
            //优先考虑全屏
            for (option in choices) {
                if (option.height == screenWidth && option.width == screenHeight) {
                    return Size(option.width, option.height)
                }
            }
            //没有全屏尺寸，选择同比例中间尺寸
            val aspectRatioSize = choices.filter {
                screenWidth * it.width == screenHeight * it.height
            }
            //选取中间的尺寸
            return aspectRatioSize[aspectRatioSize.size / 2]
        }

        @JvmStatic
        fun newInstance(): Camera2BasicFragment = Camera2BasicFragment()
    }
}
