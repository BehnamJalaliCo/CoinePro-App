@file:Suppress("unused")

package androidx.activity

class BackEventCompat(val touchX: Float = 0f, val touchY: Float = 0f, val progress: Float = 0f, val swipeEdge: Int = 0) {
    companion object {
        const val EDGE_LEFT = 0
        const val EDGE_RIGHT = 1
    }
}

open class ComponentActivity : android.app.Activity()
