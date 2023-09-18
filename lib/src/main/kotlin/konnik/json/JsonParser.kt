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

/**
 * Try parsing a string into a JsonValue.
 *
 * @return json value or null if parsing fails
 */
fun parseJson(input: String): JsonValue? =
    when (val result = jsonParser(input)) {
            null -> null
            else -> when (result.second) {
                "" -> result.first
                else -> null // it's an error if not all input is consumed
            }
    }



/**
 * Consumes zero or more whitespace characters.
 */
val ws: Parser<String> = oneOf(
    s("\u0020") + _ws(),
    s("\u000A") + _ws(),
    s("\u000D") + _ws(),
    s("\u0009") + _ws(),
    succeed("")
)

fun _ws(): Parser<String> = lazy { ws }


/**
 * Matches a single digit 1 to 9.
 */
val onenine: Parser<String> =
    match { it in '1'..'9' }.map { it.toString() }

/**
 * Matches a single digit 0 to 9.
 */
val digit: Parser<String> = oneOf(
    s("0"),
    onenine
)

/**
 * Matches one or more digits.
 */
val digits: Parser<String> = oneOf(
    digit + _digits(),
    digit
)

fun _digits() = lazy { digits }


/**
 * Parse an integer that can start with a minus sign.
 */
val integer: Parser<String> = oneOf(
    onenine + digits,
    digit,
    s("-") + onenine + digits,
    s("-") + digits,
)

/**
 * Parse an optional fraction. A fraction starts with a dot (.)
 * followed by one or more digits.
 *
 * If no fraction is found an empty string is produced.
 */
val fraction: Parser<String> = oneOf(
    s(".") + digits,
    succeed("")
)

/**
 * Parse an optional sign. If no sign is found the empty string is produced.
 */
val sign: Parser<String> = oneOf(
    s("+"),
    s("-"),
    succeed("")
)

/**
 * Parse an optional exponent. If no exponent is found the empty string is produced.
 */
val exponent: Parser<String> = oneOf(
    s("E") + sign + digits,
    s("e") + sign + digits,
    succeed("")
)


/**
 * Parse a JSON number.
 */
val number: Parser<String> =
    integer + fraction + exponent


/**
 * Parse one hex digit (both lower and upper case allowed)
 */
val hex: Parser<String> = oneOf(
    digit,
    match { it in 'A'..'F' }.map { it.toString() },
    match { it in 'a'..'f' }.map { it.toString() },
)

/**
 * Parse an escaped value in a JSON string, i.e. a sequence
 * of characters following a backslash (\).
 */
val escape: Parser<String> = oneOf(
    s("\"").map { "\"" },
    s("\\").map { "\\" },
    s("/").map { "/" },
    s("b").map { "\b" },
    s("f").map { "\u000C" },
    s("n").map { "\n" },
    s("r").map { "\r" },
    s("t").map { "\t" },
    (s("u").map { "" } + hex + hex + hex + hex).map {
        String.format("%c", it.toInt(16))
    }
)

/**
 * Parse one character in a JSON string. The character can be escaped by a backslash (\).
 */
val character: Parser<String> = oneOf(
    match { it >= '\u0020' && it != '\"' && it != '\\' }.map { it.toString() },  // "
    s("\\").keep(escape)
)

/**
 * Parse zero or more valid characters in a JSON string.
 */
val characters: Parser<String> = oneOf(
    character + _characters(),
    succeed("")
)

fun _characters() = lazy { characters }


/**
 * Parse a JSON string.
 */
val string: Parser<String> =
    s("\"").keep(characters).skip(s("\""))

/**
 * Parse an element. An element is a json value surrounded by whitespace.
 */
val element: Parser<JsonValue> =
    ws.keep(_jsonValue()).skip(ws)

/**
 * Parse one or more elements separated by comma (,).
 */
val elements: Parser<List<JsonValue>> = oneOf(
    element.skip(s(",")).andThen { first ->
        _elements().map { rest ->
            listOf(first) + rest
        }
    },
    element.map { listOf(it) },
)

fun _elements(): Parser<List<JsonValue>> = lazy { elements }

/**
 * Parse one member. A member is one key-value pair in a JSON object.
 */
val member: Parser<Pair<String, JsonValue>> =
    ws.keep(string).skip(ws).skip(s(":")).andThen { key ->
        element.map { value ->
            key to value
        }
    }

/**
 * Parse one or more members separated by a comma (,).
 */
val members: Parser<List<Pair<String, JsonValue>>> = oneOf(
    member.skip(s(",")).andThen { first ->
        _members().map { rest ->
            listOf(first) + rest
        }
    },
    member.map { listOf(it) }
)

fun _members(): Parser<List<Pair<String, JsonValue>>> = members


/**
 * Parse a JSON boolean.
 */
val jsBool: Parser<JsonValue> = oneOf(
    s("true").map { JsonValue.Bool(true) },
    s("false").map { JsonValue.Bool(false) }
)

/**
 * Parse a JSON null.
 */
val jsNull: Parser<JsonValue> =
    s("null").map { JsonValue.Null }


/**
 * Parse a JSON number.
 */
val jsNum: Parser<JsonValue> =
    number.map { JsonValue.Num(it.toDouble()) }

/**
 * Parse a JSON string.
 */
val jsString: Parser<JsonValue> =
    string.map { JsonValue.Str(it) }

/**
 * Parse a JSON array.
 */
val jsArray: Parser<JsonValue> = oneOf(
    s("[").andThen { ws }.andThen { s("]") }.map { JsonValue.Array(emptyList<JsonValue>()) },
    s("[").keep(_elements()).skip(s("]")).map { JsonValue.Array(it) },
)


/**
 * Parse a JSON object.
 */
val jsObject: Parser<JsonValue> = oneOf(
    s("{").keep(members).skip(s("}")).map { JsonValue.Object(it.toMap()) },
    s("{").skip(ws).keep(s("}")).map { JsonValue.Object(emptyMap()) },
)

/**
 * Parse a JSON value.
 */
val jsonValue: Parser<JsonValue> = oneOf(
    jsNull,
    jsBool,
    jsString,
    jsNum,
    jsArray,
    jsObject
)

fun _jsonValue() = lazy { jsonValue }


val jsonParser: Parser<JsonValue> = element