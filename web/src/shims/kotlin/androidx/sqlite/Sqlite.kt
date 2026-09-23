package androidx.sqlite

interface SQLiteConnection : AutoCloseable {
    fun prepare(sql: String): SQLiteStatement
}

interface SQLiteStatement : AutoCloseable {
    fun step(): Boolean
}

fun SQLiteConnection.execSQL(sql: String) {
    prepare(sql).use { it.step() }
}
