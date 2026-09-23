@file:Suppress("unused")

package androidx.activity.result

import androidx.activity.result.contract.ActivityResultContracts

class PickVisualMediaRequest internal constructor(
    val mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType,
    @Suppress("UNUSED_PARAMETER") internal val built: Boolean,
)

fun PickVisualMediaRequest(
    mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo,
): PickVisualMediaRequest = PickVisualMediaRequest(mediaType, true)

class ActivityResult(val resultCode: Int, val data: android.content.Intent?)

abstract class ActivityResultLauncher<I> {
    abstract fun launch(input: I)
    fun launch(input: I, options: Any?) = launch(input)
    open fun unregister() {}
}
