package androidx.datastore.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.LocalStoragePreferences
import androidx.datastore.preferences.core.Preferences
import kotlin.properties.ReadOnlyProperty

/** `val Context.store by preferencesDataStore("name")` — the page's store of that name. */
fun preferencesDataStore(
    name: String,
    corruptionHandler: Any? = null,
    produceMigrations: (Context) -> List<Any> = { emptyList() },
    scope: Any? = null,
): ReadOnlyProperty<Context, DataStore<Preferences>> = ReadOnlyProperty { _, _ -> LocalStoragePreferences.named(name) }

fun Context.preferencesDataStoreFile(name: String): java.io.File = java.io.File("datastore/$name.preferences_pb")
