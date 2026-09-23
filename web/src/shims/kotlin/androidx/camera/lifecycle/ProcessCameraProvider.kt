@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.camera.lifecycle

import androidx.camera.core.BrowserCamera
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.view.PreviewView
import com.google.common.util.concurrent.ListenableFuture

/** The browser's camera through `getUserMedia`; bound use cases share one stream. */
class ProcessCameraProvider private constructor() {
    private val running = ArrayList<PreviewView>()

    fun bindToLifecycle(owner: Any?, selector: CameraSelector, vararg useCases: UseCase): Camera {
        val view = useCases.filterIsInstance<Preview>().firstNotNullOfOrNull { it.surfaceProvider?.view }
        if (view != null) {
            running += view
            BrowserCamera.start(view, selector) {}
            useCases.filterIsInstance<ImageCapture>().forEach { it.video = view.video }
        }
        return object : Camera {}
    }

    fun unbindAll() {
        running.forEach(BrowserCamera::stop)
        running.clear()
    }

    fun unbind(vararg useCases: UseCase) = unbindAll()

    companion object {
        private val instance = ProcessCameraProvider()
        fun getInstance(context: android.content.Context): ListenableFuture<ProcessCameraProvider> = ListenableFuture.of(instance)
    }
}
