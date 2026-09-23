@file:Suppress("unused", "UNCHECKED_CAST")

package androidx.room

import kotlin.reflect.KClass

/*
 * Room, for the browser: the annotations the phone's entities and DAOs carry, and a builder that
 * hands back the browser's own implementation of a database — registered by the page at start
 * (`WebRoom.register`), because a Wasm build has no annotation processor to generate one.
 */

@Target(AnnotationTarget.CLASS) annotation class Entity(
    val tableName: String = "",
    val primaryKeys: Array<String> = [],
    val indices: Array<Index> = [],
    val foreignKeys: Array<ForeignKey> = [],
)
annotation class Index(vararg val value: String = [], val name: String = "", val unique: Boolean = false)
annotation class ForeignKey(val entity: KClass<*>, val parentColumns: Array<String>, val childColumns: Array<String>, val onDelete: Int = 1)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class PrimaryKey(val autoGenerate: Boolean = false)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class ColumnInfo(val name: String = "", val defaultValue: String = "")
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class Ignore
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class Embedded(val prefix: String = "")
@Target(AnnotationTarget.CLASS) annotation class Dao
@Target(AnnotationTarget.CLASS) annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true, val views: Array<KClass<*>> = [])
@Target(AnnotationTarget.CLASS) annotation class TypeConverters(vararg val value: KClass<*>)
@Target(AnnotationTarget.FUNCTION) annotation class TypeConverter
@Target(AnnotationTarget.FUNCTION) annotation class Query(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT, val entity: KClass<*> = Any::class)
@Target(AnnotationTarget.FUNCTION) annotation class Update(val onConflict: Int = OnConflictStrategy.ABORT, val entity: KClass<*> = Any::class)
@Target(AnnotationTarget.FUNCTION) annotation class Delete(val entity: KClass<*> = Any::class)
@Target(AnnotationTarget.FUNCTION) annotation class Upsert(val entity: KClass<*> = Any::class)
@Target(AnnotationTarget.FUNCTION) annotation class Transaction
@Target(AnnotationTarget.FUNCTION) annotation class RawQuery

object OnConflictStrategy {
    const val NONE = 0
    const val REPLACE = 1
    const val ROLLBACK = 2
    const val ABORT = 3
    const val FAIL = 4
    const val IGNORE = 5
}

abstract class RoomDatabase {
    open fun close() {}
    open fun clearAllTables() {}

    class Builder<T : RoomDatabase> internal constructor(private val klass: KClass<T>, private val name: String?) {
        fun addMigrations(vararg migrations: androidx.room.migration.Migration): Builder<T> = this
        fun fallbackToDestructiveMigration(dropAllTables: Boolean = true): Builder<T> = this
        fun fallbackToDestructiveMigration(): Builder<T> = this
        fun allowMainThreadQueries(): Builder<T> = this
        fun enableMultiInstanceInvalidation(): Builder<T> = this
        fun setJournalMode(mode: Any?): Builder<T> = this
        fun addCallback(callback: Any?): Builder<T> = this
        fun build(): T = WebRoom.open(klass, name)
    }
}

object Room {
    fun <T : RoomDatabase> databaseBuilder(context: android.content.Context, klass: KClass<T>, name: String): RoomDatabase.Builder<T> =
        RoomDatabase.Builder(klass, name)
    fun <T : RoomDatabase> inMemoryDatabaseBuilder(context: android.content.Context, klass: KClass<T>): RoomDatabase.Builder<T> =
        RoomDatabase.Builder(klass, null)
}

/** Where the page registers its implementation of each database class, before any is opened. */
object WebRoom {
    private val factories = HashMap<KClass<*>, (String?) -> RoomDatabase>()
    private val open = HashMap<String, RoomDatabase>()
    fun <T : RoomDatabase> register(klass: KClass<T>, factory: (name: String?) -> T) { factories[klass] = factory }
    internal fun <T : RoomDatabase> open(klass: KClass<T>, name: String?): T {
        val key = "${klass.simpleName}:$name"
        return open.getOrPut(key) {
            val factory = factories[klass] ?: error("No browser implementation registered for ${klass.simpleName}")
            factory(name)
        } as T
    }
}
