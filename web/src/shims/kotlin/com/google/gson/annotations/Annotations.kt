package com.google.gson.annotations

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class SerializedName(val value: String, val alternate: Array<String> = [])

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Expose(val serialize: Boolean = true, val deserialize: Boolean = true)

annotation class JsonAdapter(val value: kotlin.reflect.KClass<*>, val nullSafe: Boolean = true)
