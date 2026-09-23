package javax.inject

@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class Inject
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION) annotation class Singleton
@Target(AnnotationTarget.ANNOTATION_CLASS) annotation class Qualifier
@Target(AnnotationTarget.ANNOTATION_CLASS) annotation class Scope
annotation class Named(val value: String)
interface Provider<T> { fun get(): T }
