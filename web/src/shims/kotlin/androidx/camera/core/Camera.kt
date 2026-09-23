@file:Suppress("unused", "UNUSED_PARAMETER")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package androidx.camera.core

import androidx.camera.view.PreviewView

private fun startCameraJs(video: JsAny, back: Boolean, done: (Boolean) -> Unit): Unit = js(
    """(function () {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { done(false); return; }
        navigator.mediaDevices.getUserMedia({ video: { facingMode: back ? 'environment' : 'user' }, audio: false })
            .then(function (s) { video.srcObject = s; video.play(); done(true); }, function () { done(false); });
    })()""",
)
private fun stopCameraJs(video: JsAny): Unit = js("(function(){ var s = video.srcObject; if (s) { s.getTracks().forEach(function(t){ t.stop(); }); video.srcObject = null; } })()")
private fun grabJs(video: JsAny, done: (JsAny?) -> Unit): Unit = js(
    """(function () {
        var w = video.videoWidth, h = video.videoHeight;
        if (!w || !h) { done(null); return; }
        var c = document.createElement('canvas'); c.width = w; c.height = h;
        c.getContext('2d').drawImage(video, 0, 0, w, h);
        c.toBlob(function (b) { if (!b) { done(null); return; } b.arrayBuffer().then(function (a) { done(new Int8Array(a)); }); }, 'image/jpeg', 0.92);
    })()""",
)

class CameraSelector private constructor(val back: Boolean) {
    companion object {
        val DEFAULT_BACK_CAMERA = CameraSelector(true)
        val DEFAULT_FRONT_CAMERA = CameraSelector(false)
    }
}

interface UseCase

class Preview private constructor() : UseCase {
    class SurfaceProvider internal constructor(internal val view: PreviewView)
    var surfaceProvider: SurfaceProvider? = null
    fun setSurfaceProvider(provider: SurfaceProvider?) { surfaceProvider = provider }
    class Builder { fun build(): Preview = Preview() }
}

class ImageCaptureException(val imageCaptureError: Int, message: String, cause: Throwable?) : Exception(message, cause)

/** A still from the running preview, drawn to a canvas and written as a JPEG. */
class ImageCapture private constructor() : UseCase {
    internal var video: JsAny? = null

    class Builder {
        fun setCaptureMode(mode: Int): Builder = this
        fun setTargetRotation(rotation: Int): Builder = this
        fun build(): ImageCapture = ImageCapture()
    }

    class OutputFileOptions private constructor(internal val file: java.io.File) {
        class Builder(private val file: java.io.File) { fun build(): OutputFileOptions = OutputFileOptions(file) }
    }

    class OutputFileResults internal constructor(val savedUri: android.net.Uri?)

    interface OnImageSavedCallback {
        fun onImageSaved(outputFileResults: OutputFileResults)
        fun onError(exception: ImageCaptureException)
    }

    fun takePicture(options: OutputFileOptions, executor: java.util.concurrent.Executor, callback: OnImageSavedCallback) {
        val source = video ?: return callback.onError(ImageCaptureException(ERROR_CAMERA_CLOSED, "Camera is closed", null))
        grabJs(source) { bytes ->
            executor.execute {
                if (bytes == null) {
                    callback.onError(ImageCaptureException(ERROR_CAPTURE_FAILED, "Capture failed", null))
                } else {
                    runCatching { options.file.writeBytes(com.coinepro.web.net.jsBytesPublic(bytes)) }
                        .onSuccess { callback.onImageSaved(OutputFileResults(android.net.Uri.fromFile(options.file))) }
                        .onFailure { callback.onError(ImageCaptureException(ERROR_FILE_IO, it.message ?: "", it)) }
                }
            }
        }
    }

    companion object {
        const val CAPTURE_MODE_MINIMIZE_LATENCY = 1
        const val CAPTURE_MODE_MAXIMIZE_QUALITY = 0
        const val ERROR_UNKNOWN = 0
        const val ERROR_FILE_IO = 1
        const val ERROR_CAPTURE_FAILED = 2
        const val ERROR_CAMERA_CLOSED = 3
    }
}

internal object BrowserCamera {
    fun start(view: PreviewView, selector: CameraSelector, done: (Boolean) -> Unit) = startCameraJs(view.video, selector.back, done)
    fun stop(view: PreviewView) = stopCameraJs(view.video)
}

interface Camera
