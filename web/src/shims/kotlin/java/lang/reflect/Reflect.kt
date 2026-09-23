package java.lang.reflect

/**
 * A type as `Gson` and `TypeToken` pass it around. In the browser it is a class plus its type
 * arguments, written out by whoever made it — `TypeToken`'s anonymous subclass trick has no
 * reflection to stand on here, so the shared code's few `TypeToken`s become `WireType`s.
 */
interface Type

class WireType(val raw: kotlin.reflect.KClass<*>, val arguments: List<Type> = emptyList()) : Type {
    override fun equals(other: Any?): Boolean = other is WireType && other.raw == raw && other.arguments == arguments
    override fun hashCode(): Int = raw.hashCode() * 31 + arguments.hashCode()
    override fun toString(): String = raw.simpleName + if (arguments.isEmpty()) "" else arguments.joinToString(",", "<", ">")
}

interface ParameterizedType : Type
