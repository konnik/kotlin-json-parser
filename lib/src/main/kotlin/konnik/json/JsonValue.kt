package konnik.json

/**
 * Algebraic Data Type representing a parsed JSON-value.
 */
sealed interface JsonValue {
    data object Null : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Array(val items: List<JsonValue>) : JsonValue
    data class Object(val members: Map<String, JsonValue>) : JsonValue
}