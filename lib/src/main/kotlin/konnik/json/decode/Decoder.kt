package konnik.json.decode

import konnik.json.parser.JsonValue
import konnik.json.result.*


/**
 * Let's define a json decoder.
 *
 * A Decoder<T> is a just function that takes some json and produces a
 * result with a value of type T.
 *
 * If the json matches the expectations of the decoder that is, otherwise
 * it will fail with an error message,
 *
 */
typealias Decoder<T> = (JsonValue) -> Result<T>

// -------------------------------
// PRIMITIVE DECODERS
// -------------------------------

/**
 * Decodes a json number into an integer. Will fail if the json number
 * is not an integer.
 */
val int: Decoder<Int> = { value ->
    when (value) {
        is JsonValue.Num ->
            if (value.value.toInt().toDouble() == value.value)
                Ok(value.value.toInt())
            else
                Err("${value.str()} is not an integer")

        else -> Err("${value.str()} is not an integer")
    }
}

/**
 * Decodes a json number into a double.
 */
val double: Decoder<Double> = { value ->
    when (value) {
        is JsonValue.Num ->
            Ok(value.value)

        else -> Err("${value.str()} is not a double")
    }
}

/**
 * Decodes a json boolean value.
 */
val bool: Decoder<Boolean> = { jsonValue ->
    when (jsonValue) {
        is JsonValue.Bool ->
            Ok(jsonValue.value)

        else -> Err("${jsonValue.str()} is not a boolean")
    }
}

/**
 * Decodes a json string.
 */
val str: Decoder<String> = { value ->
    when (value) {
        is JsonValue.Str ->
            Ok(value.value)

        else -> Err("${value.str()} is not a string")
    }
}

/**
 * Decodes a json array into a [List]. The provided [itemDecoder] is used
 * to decode the individual items.
 */
fun <T> list(itemDecoder: Decoder<T>): Decoder<List<T>> = { jsonValue ->
    when (jsonValue) {
        is JsonValue.Array ->
            jsonValue.items.fold(Ok(emptyList())) { acc, item ->
                map(acc, itemDecoder(item)) { a, b -> a + b }
            }

        else -> Err("${jsonValue.str()} is not an array")
    }
}

/**
 * Represents a list without an item decoder.
 */
interface ListOf {
    /**
     * Decodes a list with the provided decoder.
     */
    infix fun <T> of(itemDecoder: Decoder<T>): Decoder<List<T>> = list(itemDecoder)

}

/**
 * Decodes a list where the item decoder can be specified later using the
 * infix operator [ListOf::of].
 */
val list: ListOf = object : ListOf {}


/**
 * Decodes the field [fieldName] in a json object using the provided [fieldDecoder]
 */
fun <T> field(fieldName: String, fieldDecoder: Decoder<T>): Decoder<T> =
    { json ->
        when (json) {
            is JsonValue.Object ->
                json.members[fieldName]?.let { fieldDecoder(it) }
                    ?: Err("Field '$fieldName' not found")

            else -> Err("Expecting a JSON object with field '$fieldName' but was ${json.str()}")
        }
    }

/**
 * Represents a selected field without a decoder.
 */
data class FieldOf(val name: String) {
    /**
     * Returns a decoder to decode the value for the selected field.
     */
    infix fun <T> of(decoder: Decoder<T>): Decoder<T> = field(name, decoder)
}

/**
 * Selects a field to decode.
 */
fun field(name: String): FieldOf = FieldOf(name)

/**
 * Decodes nested json objects requiring the provided fields.
 */
fun <T : Any> at(path: List<String>, decoder: Decoder<T>): Decoder<T> = { jsonValue ->
    when {
        path.isEmpty() -> decoder(jsonValue)
        else -> field(path.first(), value)(jsonValue).andThen { subValue ->
            at(path.drop(1), decoder)(subValue)
        }
    }
}

/**
 * A decoder that just returns the [JsonValue] without decoding it.
 */
val value: Decoder<JsonValue> = { jsonValue -> Ok(jsonValue) }


// ------------------------------
// COMBINATOR FUNCTIONS
// ------------------------------

/**
 * Creates a decoder that will always succeed with the given value.
 */
fun <A> succeed(value: A): Decoder<A> = { _ -> Ok(value) }


/**
 * Creates a decoder that will always fail.
 */
fun <A> fail(description: String): Decoder<A> = { _ -> Err(description) }

/**
 * Decode a nullable value.
 */
fun <T : Any> nullable(decoder: Decoder<T>): Decoder<T?> =
    { json ->
        when (json) {
            is JsonValue.Null -> Ok(null)
            else -> decoder(json)
        }
    }

/**
 * Maps a decoded value using the provided [transform] function.
 *
 * Yes, you're right, this makes our Decoder type a functor!
 */
fun <A, B> Decoder<A>.map(transform: (A) -> B): Decoder<B> = { jsonValue ->
    this(jsonValue).map(transform)
}

/**
 * Maps a decoded value using the provided [transform] function.
 */
@JvmName("mapDecoderAsParam")
fun <A, B> map(decoder: Decoder<A>, transform: (A) -> B): Decoder<B> = { jsonValue ->
    decoder(jsonValue).map(transform)
}

/**
 * Combine the results of two decoders using the provided function.
 */
fun <A, B, R> map(a: Decoder<A>, b: Decoder<B>, transform: (A, B) -> R): Decoder<R> =
    a.andThen { aValue ->
        b.map { bValue -> transform(aValue, bValue) }
    }

/**
 * Combine the results of three decoders.
 */
fun <A, B, C, R> map(a: Decoder<A>, b: Decoder<B>, c: Decoder<C>, transform: (A, B, C) -> R): Decoder<R> =
    a.andThen { aValue -> map(b, c) { bValue, cValue -> transform(aValue, bValue, cValue) } }

/**
 * Combine the results of four decoders.
 */
fun <A, B, C, D, R> map(
    a: Decoder<A>,
    b: Decoder<B>,
    c: Decoder<C>,
    d: Decoder<D>,
    transform: (A, B, C, D) -> R
): Decoder<R> =
    a.andThen { aValue -> map(b, c, d) { bValue, cValue, dValue -> transform(aValue, bValue, cValue, dValue) } }

/**
 * Combine the results of five decoders.
 *
 * Note: if you need to combine more than five decoders you can
 * always use [andThen].
 */
fun <A, B, C, D, E, R> map(
    a: Decoder<A>,
    b: Decoder<B>,
    c: Decoder<C>,
    d: Decoder<D>,
    e: Decoder<E>,
    transform: (A, B, C, D, E) -> R
): Decoder<R> =
    a.andThen { aValue ->
        map(b, c, d, e) { bValue, cValue, dValue, eValue ->
            transform(aValue, bValue, cValue, dValue, eValue)
        }
    }

/**
 * Sequence two decoders together where the second decoder depends
 * on the result of the first.
 *
 * If you think this sounds a lot like a Monad you're completely right!
 */
fun <A, B> Decoder<A>.andThen(aToDecoderB: (A) -> Decoder<B>): Decoder<B> = { json ->
    when (val a = this(json)) {
        is Ok -> aToDecoderB(a.value)(json)
        is Err -> a
    }
}

/**
 * Combine a list of decoders into a decoder that returns a list.
 */
fun <T> List<Decoder<T>>.combine(): Decoder<List<T>> =
    this.fold(succeed(emptyList())) { acc, item ->
        map(acc, item) { a, b ->
            a + b
        }
    }


/**
 * Combines two decoders. If the first one fails try the other.
 */
fun <T : Any> Decoder<T>.or(other: Decoder<T>): Decoder<T> = { jsonValue ->
    when (val result = this(jsonValue)) {
        is Err -> other(jsonValue)
        is Ok -> result
    }
}

/**
 * Try a bunch of different decoders and return the result of the first one that succeeds.
 */
fun <T : Any> oneOf(first: Decoder<T>, vararg rest: Decoder<T>): Decoder<T> =
    rest.fold(first) { acc, next ->
        acc.or(next)
    }.withError { "oneOf failed to decode ${it.str()}" }


/**
 * Creates an error message from the [JsonValue].
 */
fun <A> Decoder<A>.withError(toMessage: (JsonValue) -> String): Decoder<A> = { jsonValue ->
    when (val result = this(jsonValue)) {
        is Err -> Err(toMessage(jsonValue))
        is Ok -> result
    }
}

/**
 * Converts a [JsonValue] to its json representation. Used internally to
 * construct error messages.
 */
private fun JsonValue.str(): String =
    when (this) {
        is JsonValue.Array -> items.joinToString(prefix = "[", separator = ", ", postfix = "]") { it.str() }
        is JsonValue.Bool -> value.toString()
        is JsonValue.Null -> "null"
        is JsonValue.Num -> value.toString()
        is JsonValue.Object ->
            members.entries.joinToString(prefix = "{", separator = ", ", postfix = "}") {
                "\"${it.key}\":" + it.value.str()
            }

        is JsonValue.Str -> "\"" + value + "\""
    }