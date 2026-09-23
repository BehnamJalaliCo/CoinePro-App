package dagger.hilt

@Target(AnnotationTarget.CLASS) annotation class InstallIn(vararg val value: kotlin.reflect.KClass<*>)
@Target(AnnotationTarget.CLASS) annotation class EntryPoint
