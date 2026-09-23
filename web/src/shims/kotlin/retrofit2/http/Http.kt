package retrofit2.http

@Target(AnnotationTarget.FUNCTION) annotation class GET(val value: String = "")
@Target(AnnotationTarget.FUNCTION) annotation class POST(val value: String = "")
@Target(AnnotationTarget.FUNCTION) annotation class PUT(val value: String = "")
@Target(AnnotationTarget.FUNCTION) annotation class PATCH(val value: String = "")
@Target(AnnotationTarget.FUNCTION) annotation class DELETE(val value: String = "")
@Target(AnnotationTarget.FUNCTION) annotation class HEAD(val value: String = "")
@Target(AnnotationTarget.FUNCTION) annotation class HTTP(val method: String, val path: String = "", val hasBody: Boolean = false)
@Target(AnnotationTarget.FUNCTION) annotation class Headers(vararg val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Streaming
@Target(AnnotationTarget.FUNCTION) annotation class Multipart
@Target(AnnotationTarget.FUNCTION) annotation class FormUrlEncoded
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Url
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Body
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Query(val value: String, val encoded: Boolean = false)
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class QueryMap(val encoded: Boolean = false)
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Path(val value: String, val encoded: Boolean = false)
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Header(val value: String)
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class HeaderMap
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Part(val value: String = "")
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class PartMap
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Field(val value: String)
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class FieldMap
@Target(AnnotationTarget.VALUE_PARAMETER) annotation class Tag
