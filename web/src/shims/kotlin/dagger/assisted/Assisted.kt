package dagger.assisted

@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Assisted(val value: String = "")
@Target(AnnotationTarget.CONSTRUCTOR) annotation class AssistedInject
@Target(AnnotationTarget.CLASS) annotation class AssistedFactory
