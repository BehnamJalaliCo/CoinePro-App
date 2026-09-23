@file:Suppress("unused")

package androidx.activity.result.contract

import android.net.Uri

abstract class ActivityResultContract<I, O>

/** The contracts the phone's screens launch, each answered by the browser's nearest equivalent. */
object ActivityResultContracts {
    open class CreateDocument(val mimeType: String = "*/*") : ActivityResultContract<String, Uri?>()
    open class OpenDocument : ActivityResultContract<Array<String>, Uri?>()
    open class GetContent : ActivityResultContract<String, Uri?>()
    open class RequestPermission : ActivityResultContract<String, Boolean>()
    open class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>()
    open class TakePicture : ActivityResultContract<Uri, Boolean>()
    open class StartActivityForResult : ActivityResultContract<android.content.Intent, androidx.activity.result.ActivityResult>()

    open class PickVisualMedia : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType { val accept: String }
        object ImageOnly : VisualMediaType { override val accept = "image/*" }
        object VideoOnly : VisualMediaType { override val accept = "video/*" }
        object ImageAndVideo : VisualMediaType { override val accept = "image/*,video/*" }
        class SingleMimeType(val mimeType: String) : VisualMediaType { override val accept = mimeType }
    }
}
