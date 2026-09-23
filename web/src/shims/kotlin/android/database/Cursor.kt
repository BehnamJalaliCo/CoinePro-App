package android.database

/** One row with the display name of a picked file — what `OpenableColumns` queries read. */
class Cursor(private val name: String?) : AutoCloseable {
    private var position = -1
    fun moveToFirst(): Boolean { position = 0; return name != null }
    fun getColumnIndex(column: String): Int = if (column == "_display_name") 0 else if (column == "_size") 1 else -1
    fun getString(index: Int): String? = if (index == 0) name else null
    fun getLong(index: Int): Long = 0
    fun isNull(index: Int): Boolean = index != 0
    val count: Int get() = if (name == null) 0 else 1
    override fun close() {}
}
