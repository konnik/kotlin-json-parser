package konnik.json.encode

import konnik.json.JsonValue

/**
 * Encodes a [JsonValue] as JSON string.
 */
fun encodeJson(json: JsonValue): String =
    when (json) {
        is JsonValue.Null -> "null"
        is JsonValue.Bool -> if (json.value) "true" else "false"
        is JsonValue.Num -> json.value.toString()
        is JsonValue.Str -> "\"${escapeJsonString(json.value)}\""
        is JsonValue.Array -> json.items.joinToString(
            prefix = "[",
            separator = ",",
            postfix = "]",
            transform = ::encodeJson
        )

        is JsonValue.Object -> json.members.entries.joinToString(
            prefix = "{", separator = ",", postfix = "}"
        ) { (key, value) ->
            "\"${escapeJsonString(key)}\":${encodeJson(value)}"
        }
    }

private fun escapeJsonString(value: String): String {
    val sb = StringBuilder()
    value.forEach {
        when (it) {
            '"' -> sb.append("\\$it")
            '\\' -> sb.append("\\$it")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(it)
        }
    }
    return sb.toString()
}

// Basic functions for encoding native Kotlin types into JsonValue's.

/**
 * Encodes a [String] into a [JsonValue.Str].
 */
fun str(value: String): JsonValue = JsonValue.Str(value)

/**
 * Encodes a [Int] into a [JsonValue.Num].
 */
fun number(value: Int): JsonValue = JsonValue.Num(value.toDouble())

/**
 * Encodes a [Long] into a [JsonValue.Num].
 */
fun number(value: Long): JsonValue = JsonValue.Num(value.toDouble())

/**
 * Encodes a [Float] into a [JsonValue.Num].
 */
fun number(value: Float): JsonValue = JsonValue.Num(value.toDouble())

/**
 * Encodes a [Double] into a [JsonValue.Num].
 */
fun number(value: Double): JsonValue = JsonValue.Num(value)

/**
 * Encodes a [Boolean] into a [JsonValue.Bool].
 */
fun bool(value: Boolean): JsonValue = JsonValue.Bool(value)

/**
 * Creates a [JsonValue.Null] value,
 */
fun nullValue(): JsonValue = JsonValue.Null

/**
 * Encodes a List of [String] into a [JsonValue.Array].
 */
fun array(vararg items: String): JsonValue =
    JsonValue.Array(items.map(JsonValue::Str))

/**
 * Encodes a List of [Float] into a [JsonValue.Array].
 */
fun array(vararg items: Float): JsonValue =
    JsonValue.Array(items.map { JsonValue.Num(it.toDouble()) })

/**
 * Encodes a List of [Double] into a [JsonValue.Array].
 */
fun array(vararg items: Double): JsonValue =
    JsonValue.Array(items.map { JsonValue.Num(it) })

/**
 * Encodes a List of [Int] into a [JsonValue.Array].
 */
fun array(vararg items: Int): JsonValue =
    JsonValue.Array(items.map { JsonValue.Num(it.toDouble()) })

/**
 * Encodes a List of [Long] into a [JsonValue.Array].
 */
fun array(vararg items: Long): JsonValue =
    JsonValue.Array(items.map { JsonValue.Num(it.toDouble()) })

/**
 * Encodes a List of [JsonValue] into a [JsonValue.Array].
 */
fun array(vararg items: JsonValue): JsonValue =
    JsonValue.Array(items.toList())

/**
 * Encodes a list of Pair<String, JsonValue> into a [JsonValue.Object].
 */
fun obj(vararg members: Pair<String, JsonValue>): JsonValue =
    JsonValue.Object(members.toMap())

/**
 * Encodes a Map<String, JsonValue> into a [JsonValue.Object].
 */
fun obj(members: Map<String, JsonValue>): JsonValue =
    JsonValue.Object(members)

/**
 * Dsl for building a [JsonValue.Object].
 */
fun obj(block: ObjectBuilder.() -> Unit): JsonValue {
    val builder = ObjectBuilder()
    builder.block()
    return builder.build()
}


class ObjectBuilder {
    private val members: MutableMap<String, JsonValue> = mutableMapOf()

    fun build(): JsonValue = JsonValue.Object(members)

    infix fun String.to(value: Int) {
        members[this] = number(value)
    }

    infix fun String.to(value: Long) {
        members[this] = number(value)
    }

    infix fun String.to(value: Float) {
        members[this] = number(value)
    }

    infix fun String.to(value: Double) {
        members[this] = number(value)
    }

    infix fun String.to(value: String) {
        members[this] = str(value)
    }

    infix fun String.to(value: Boolean) {
        members[this] = bool(value)
    }

    infix fun String.to(value: JsonValue) {
        members[this] = value
    }

    infix fun String.to(values: List<JsonValue>) {
        members[this] = JsonValue.Array(values)
    }

}
