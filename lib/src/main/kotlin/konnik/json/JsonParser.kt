package konnik.json

/* ----------------------------------------------------------------------------
 *
 * PUBLIC INTERFACE
 *
 * ----------------------------------------------------------------------------
 */

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
    when (val result = json(input)) {
        null -> null
        else -> when (result.second) {
            "" -> result.first
            else -> null // it's an error if not all input is consumed
        }
    }


/* ----------------------------------------------------------------------------
 *
 * IMPLEMENTATION
 *
 * ----------------------------------------------------------------------------
 */

/**
 * Type that defines what a parser is.
 *
 * A parser is a function that takes some input (String) as it's argument and
 * if it succeeds returns a value of type A (together with de remaining input),
 * otherwise it returns null.
 *
 */
private typealias Parser<A> = (String) -> Pair<A, String>?


/* ----------------------------------------------------------------------------
 *
 * COMBINATOR FUNCTIONS
 *
 * ----------------------------------------------------------------------------
 */

/**
 * Transforms the value of a successful parse to another value.
 *
 * Fun fact: this means that our Parser type is a Functor and corresponds to fmap in Haskell.
 */
private fun <A : Any, B : Any> Parser<A>.map(transform: (A) -> B): Parser<B> = { input ->
    this(input)?.let { transform(it.first) to it.second }
}

/**
 * A parser that always succeeds with the provided value without consuming any input.
 *
 * Fun fact: this corresponds to pure (from type class Applicative) in Haskell.
 */

private fun <T : Any> succeed(value: T): Parser<T> = { input -> value to input }

/**
 * Chain two parsers together where the second parser depends on the produced value
 * if the first. This means that the first parser must succeed before the second parser can
 * proceed.
 *
 * This function is often called flatMap, but I think andThen is a more intuitive name. It's
 * also the name used in the Elm, the best programming language ever created, so let's go with that.
 *
 * Fun fact: This is bind / >>= in Haskell and makes our Parser type a Monad.
 */
private fun <A : Any, B : Any> Parser<A>.andThen(aToParserB: (A) -> Parser<B>): Parser<B> = { input ->
    this(input)?.let { a -> aToParserB(a.first)(a.second) }
}

/**
 * Chain two parsers together but keep only the value from de second parser.
 *
 * Fun fact: This is Applicative *> (or Monad >>) in Haskell
 */
private fun <A : Any, B : Any> Parser<A>.keep(parserB: Parser<B>): Parser<B> =
    this.andThen { parserB }


/**
 * Chain two parsers together but keep only the value from de first parser.
 *
 * Fun fact: This is Applicative <* in Haskell
 */
private fun <A : Any, B : Any> Parser<A>.skip(parserB: Parser<B>): Parser<A> =
    this.andThen { a -> parserB.map { a } }

/**
 * Combine multiple parsers into a parser that returns the value of the first
 * parser that succeeds.
 */
private fun <A : Any> oneOf(vararg parsers: Parser<A>): Parser<A> = { input ->
    parsers.firstNotNullOfOrNull { it(input) }
}

/**
 * Make the construction of a parser lazy. This is sometimes useful for
 * preventing stack overflows when defining parsers recursively.
 */
private fun <A : Any> lazy(parser: () -> Parser<A>): Parser<A> = { input ->
    parser()(input)
}

/**
 * Combine to parsers into one that concatenates the results of the
 * individual parsers into one string.
 */
private operator fun <A : Any, B : Any> Parser<A>.plus(parserB: Parser<B>): Parser<String> =
    this.andThen { valueA -> parserB.map { valueB -> valueA.toString() + valueB.toString() } }


/* ----------------------------------------------------------------------------
 *
 * PRIMITIVE PARSERS
 *
 * ----------------------------------------------------------------------------
 */

/**
 * Exact match of a string literal.
 */
private fun s(str: String): Parser<String> = { input ->
    when {
        input.startsWith(str) -> str to input.drop(str.length)
        else -> null
    }
}

/**
 * Match a character using the provided predicate.
 */
private fun match(test: (Char) -> Boolean): Parser<Char> = { input ->
    input.firstOrNull()?.let {
        when {
            test(it) -> input.first() to input.drop(1)
            else -> null
        }
    }
}


/* ------------------------------------------------------------------------------------------
 * GRAMMAR
 *
 * Implementation of the grammar from https://www.json.org/json-en.html follows.
 *
 * The parsers defined below are given names that follows the grammar but due to Kotlin
 * initialization orders the ordering are not the same.
 *
 * The parsers are declared as toplevel val's to minimize noise on the use site. This makes
 * the parser very readable, and it almost reads as the official grammar.
 *
 * To be able to define some recursive parsers a lazy variant of some parsers was needed.
 * This was solved by defining a corresponding function, for example:
 *
 * fun _ws() = lazy { ws }
 *
 * If there are better ways to do this I'm eager to hear.
 * ------------------------------------------------------------------------------------------
 */


/**
 * Consumes zero or more whitespace characters.
 */
private val ws: Parser<String> = oneOf(
    s("\u0020") + _ws(),
    s("\u000A") + _ws(),
    s("\u000D") + _ws(),
    s("\u0009") + _ws(),
    succeed("")
)

private fun _ws(): Parser<String> = lazy { ws }


/**
 * Matches a single digit 1 to 9.
 */
private val onenine: Parser<String> =
    match { it in '1'..'9' }.map { it.toString() }

/**
 * Matches a single digit 0 to 9.
 */
private val digit: Parser<String> = oneOf(
    s("0"),
    onenine
)

/**
 * Matches one or more digits.
 */
private val digits: Parser<String> = oneOf(
    digit + _digits(),
    digit
)

private fun _digits() = lazy { digits }


/**
 * Parse an integer that can start with a minus sign.
 */
private val integer: Parser<String> = oneOf(
    onenine + digits,
    digit,
    s("-") + onenine + digits,
    s("-") + digit,
)

/**
 * Parse an optional fraction. A fraction starts with a dot (.)
 * followed by one or more digits.
 *
 * If no fraction is found an empty string is produced.
 */
private val fraction: Parser<String> = oneOf(
    s(".") + digits,
    succeed("")
)

/**
 * Parse an optional sign. If no sign is found the empty string is produced.
 */
private val sign: Parser<String> = oneOf(
    s("+"),
    s("-"),
    succeed("")
)

/**
 * Parse an optional exponent. If no exponent is found the empty string is produced.
 */
private val exponent: Parser<String> = oneOf(
    s("E") + sign + digits,
    s("e") + sign + digits,
    succeed("")
)


/**
 * Parse a JSON number.
 */
private val number: Parser<String> =
    integer + fraction + exponent


/**
 * Parse one hex digit (both lower and upper case allowed)
 */
private val hex: Parser<String> = oneOf(
    digit,
    match { it in 'A'..'F' }.map { it.toString() },
    match { it in 'a'..'f' }.map { it.toString() },
)

/**
 * Parse an escaped value in a JSON string, i.e. a sequence
 * of characters following a backslash (\).
 */
private val escape: Parser<String> = oneOf(
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
private val character: Parser<String> = oneOf(
    match { it >= '\u0020' && it != '\"' && it != '\\' }.map { it.toString() },  // "
    s("\\").keep(escape)
)

/**
 * Parse zero or more valid characters in a JSON string.
 */
private val characters: Parser<String> = oneOf(
    character + _characters(),
    succeed("")
)

private fun _characters() = lazy { characters }


/**
 * Parse a JSON string.
 */
private val string: Parser<String> =
    s("\"").keep(characters).skip(s("\""))

/**
 * Parse an element. An element is a json value surrounded by whitespace.
 */
private val element: Parser<JsonValue> =
    ws.keep(_jsonValue()).skip(ws)

/**
 * Parse one or more elements separated by comma (,).
 */
private val elements: Parser<List<JsonValue>> = oneOf(
    element.skip(s(",")).andThen { first ->
        _elements().map { rest ->
            listOf(first) + rest
        }
    },
    element.map { listOf(it) },
)

private fun _elements(): Parser<List<JsonValue>> = lazy { elements }

/**
 * Parse one member. A member is one key-value pair in a JSON object.
 */
private val member: Parser<Pair<String, JsonValue>> =
    ws.keep(string).skip(ws).skip(s(":")).andThen { key ->
        element.map { value ->
            key to value
        }
    }

/**
 * Parse one or more members separated by a comma (,).
 */
private val members: Parser<List<Pair<String, JsonValue>>> = oneOf(
    member.skip(s(",")).andThen { first ->
        _members().map { rest ->
            listOf(first) + rest
        }
    },
    member.map { listOf(it) }
)

private fun _members(): Parser<List<Pair<String, JsonValue>>> = members


/**
 * Parse a JSON boolean.
 */
private val jsBool: Parser<JsonValue> = oneOf(
    s("true").map { JsonValue.Bool(true) },
    s("false").map { JsonValue.Bool(false) }
)

/**
 * Parse a JSON null.
 */
private val jsNull: Parser<JsonValue> =
    s("null").map { JsonValue.Null }


/**
 * Parse a JSON number.
 */
private val jsNum: Parser<JsonValue> =
    number.map { JsonValue.Num(it.toDouble()) }

/**
 * Parse a JSON string.
 */
private val jsString: Parser<JsonValue> =
    string.map { JsonValue.Str(it) }

/**
 * Parse a JSON array.
 */
private val jsArray: Parser<JsonValue> = oneOf(
    s("[").andThen { ws }.andThen { s("]") }.map { JsonValue.Array(emptyList<JsonValue>()) },
    s("[").keep(_elements()).skip(s("]")).map { JsonValue.Array(it) },
)


/**
 * Parse a JSON object.
 */
private val jsObject: Parser<JsonValue> = oneOf(
    s("{").keep(members).skip(s("}")).map { JsonValue.Object(it.toMap()) },
    s("{").skip(ws).keep(s("}")).map { JsonValue.Object(emptyMap()) },
)

/**
 * Parse a JSON value.
 */
private val jsonValue: Parser<JsonValue> = oneOf(
    jsNull,
    jsBool,
    jsString,
    jsNum,
    jsArray,
    jsObject
)

private fun _jsonValue() = lazy { jsonValue }


private val json: Parser<JsonValue> = element