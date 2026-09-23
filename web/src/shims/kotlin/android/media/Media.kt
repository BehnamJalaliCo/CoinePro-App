package android.media

class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(usage: Int): Builder = this
        fun setContentType(type: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }
    companion object {
        const val USAGE_ALARM = 4
        const val USAGE_NOTIFICATION = 5
        const val CONTENT_TYPE_SONIFICATION = 4
    }
}

object RingtoneManager {
    const val TYPE_ALARM = 4
    const val TYPE_NOTIFICATION = 2
    fun getDefaultUri(type: Int): android.net.Uri = android.net.Uri.parse("sound://default")
}
