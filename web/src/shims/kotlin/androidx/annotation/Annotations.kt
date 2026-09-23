@file:Suppress("unused")

package androidx.annotation

// ColorInt, the thread annotations, VisibleForTesting, RequiresApi, the ranges, CheckResult and CallSuper
// come from AndroidX's own multiplatform `annotation` library, which the browser build already links.

@Retention(AnnotationRetention.BINARY) annotation class StringRes
@Retention(AnnotationRetention.BINARY) annotation class DrawableRes
@Retention(AnnotationRetention.BINARY) annotation class PluralsRes
@Retention(AnnotationRetention.BINARY) annotation class ColorRes
@Retention(AnnotationRetention.BINARY) annotation class FontRes
@Retention(AnnotationRetention.BINARY) annotation class RawRes
@Retention(AnnotationRetention.BINARY) annotation class DimenRes
@Retention(AnnotationRetention.BINARY) annotation class AnyRes
@Retention(AnnotationRetention.BINARY) annotation class BoolRes
@Retention(AnnotationRetention.BINARY) annotation class IdRes
@Retention(AnnotationRetention.BINARY) annotation class IntegerRes
@Retention(AnnotationRetention.BINARY) annotation class ArrayRes
@Retention(AnnotationRetention.BINARY) annotation class WorkerThread
@Retention(AnnotationRetention.BINARY) annotation class Keep
@Retention(AnnotationRetention.BINARY) annotation class ChecksSdkIntAtLeast(val api: Int = -1, val parameter: Int = -1, val lambda: Int = -1)
@Retention(AnnotationRetention.BINARY) annotation class Px
@Retention(AnnotationRetention.BINARY) annotation class Dimension(val unit: Int = 1)

annotation class RequiresPermission(val value: String = "", val allOf: Array<String> = [], val anyOf: Array<String> = [])
