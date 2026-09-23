package androidx.room.migration

/** Kept so the phone's migrations compile; the browser's tables start at the current version. */
abstract class Migration(val startVersion: Int, val endVersion: Int) {
    open fun migrate(connection: androidx.sqlite.SQLiteConnection) {}
    open fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
}
