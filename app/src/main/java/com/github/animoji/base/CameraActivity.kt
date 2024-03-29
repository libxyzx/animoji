package com.github.animoji.base

import android.Manifest
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.os.Bundle
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.github.animoji.manager.PermissionManager
import com.github.animoji.toast


open class CameraActivity : EglActivity() {

    protected val cameraExecutor by lazy { ContextCompat.getMainExecutor(this) }

    protected var cameraWidth = 0
    protected var cameraHeight = 0
    protected var cameraRotation = 0
    protected var cameraMatrix = FloatArray(16)

    open fun onUpdate(oes: Int) {}
    open fun onAnalysis(proxy: ImageProxy) {}
    protected fun isFront(): Boolean {
        return mCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
    }

    protected fun toggle() {
        mCameraSelector = if (isFront()) CameraSelector.DEFAULT_BACK_CAMERA
        else CameraSelector.DEFAULT_FRONT_CAMERA
        openCamera()
    }

    private var mCameraSurfaceTexture: SurfaceTexture? = null
    private var mCameraSurface: Surface? = null
    private val mCameraTexture = IntArray(1)
    private val mCameraSize = Size(480, 640)
    private var mCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

    private var mPermissionManager = PermissionManager(this, Manifest.permission.CAMERA) {
        if (it) {
            openCamera()
        } else {
            toast("没有相机权限")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPermissionManager.request()
    }

    override fun onDestroy() {
        eglExecutor.execute {
            mCameraSurface?.release()
            mCameraSurface = null
            mCameraSurfaceTexture?.release()
            mCameraSurfaceTexture = null
            GLES20.glDeleteTextures(1, mCameraTexture, 0)
        }
        super.onDestroy()
    }


    private fun createCameraSurface() {
        GLES20.glGenTextures(1, mCameraTexture, 0)
        mCameraSurfaceTexture = SurfaceTexture(mCameraTexture[0])
        mCameraSurfaceTexture?.setDefaultBufferSize(cameraWidth, cameraHeight)
        mCameraSurfaceTexture?.setOnFrameAvailableListener({
            if (mCameraSurfaceTexture != null && !isDestroyed) {
                it.updateTexImage()
                it.getTransformMatrix(cameraMatrix)
                onUpdate(mCameraTexture[0])
                swapBuffer()
            }
        }, eglHandler)
        mCameraSurface = Surface(mCameraSurfaceTexture)
    }


    private fun openCamera() {
        ProcessCameraProvider.getInstance(this).apply {
            addListener({
                val provider = get()
                provider.unbindAll()
                val preview = Preview.Builder()
                    .setTargetResolution(mCameraSize)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(mCameraSize)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { onAnalysis(it) }
                if (isDestroyed)
                    return@addListener
                provider.bindToLifecycle(
                    this@CameraActivity,
                    mCameraSelector,
                    preview, analysis
                )
                preview.setSurfaceProvider(eglExecutor) { request ->
                    cameraWidth = request.resolution.width
                    cameraHeight = request.resolution.height
                    request.setTransformationInfoListener(cameraExecutor) {
                        cameraRotation = it.rotationDegrees
                    }
                    if (mCameraSurface == null)
                        createCameraSurface()
                    request.provideSurface(
                        mCameraSurface!!,
                        eglExecutor
                    ) {
                    }
                }
            }, cameraExecutor)
        }
    }
}