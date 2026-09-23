package dagger

/*
 * Dagger's annotations, kept so the phone's module compiles in the browser. The graph they
 * describe is generated for the browser by web/tools/share_sources.py as `WebGraph`.
 */
@Target(AnnotationTarget.CLASS) annotation class Module(val includes: Array<kotlin.reflect.KClass<*>> = [])
@Target(AnnotationTarget.FUNCTION) annotation class Provides
@Target(AnnotationTarget.FUNCTION) annotation class Binds
@Target(AnnotationTarget.FUNCTION) annotation class IntoMap
@Target(AnnotationTarget.FUNCTION) annotation class IntoSet
@Target(AnnotationTarget.ANNOTATION_CLASS) annotation class MapKey
interface Lazy<T> { fun get(): T }
