package konnik.json

/**
 * Data type representing a parser that parses a some input string and produces
 * a value of type A.
 *
 * If the parser succeeds the parsed value is returned together with the remainder of the input
 * that has not been consumed.
 *
 * If the parser fails null is returned.
 *
 */
typealias Parser<A> = (String) -> Pair<A, String>?


// Primitive parsers

/**
 * Exact match of a string literal.
 */
fun s(str: String): Parser<String> = { input ->
    when {
        input.startsWith(str) -> str to input.drop(str.length)
        else -> null
    }
}

/**
 * Match a character using the provided predicate.
 */
fun match(test: (Char) -> Boolean): Parser<Char> = { input ->
    input.firstOrNull()?.let {
        when {
            test(it) -> input.first() to input.drop(1)
            else -> null
        }
    }
}






fun JsonValue.str(): String =
    (this as JsonValue.Str).value

fun JsonValue.num(): Double =
    (this as JsonValue.Num).value

fun JsonValue.array(): List<JsonValue> =
    (this as JsonValue.Array).items

fun JsonValue.field(field: String): JsonValue =
    (this as JsonValue.Object).members.get(field)!!