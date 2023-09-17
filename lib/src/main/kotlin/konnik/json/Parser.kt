package konnik.json

typealias Parser<A> = (String) -> Pair<A, String>?

// combinators

fun <A : Any, B : Any> Parser<A>.map(transform: (A) -> B): Parser<B> = { input ->
    this(input)?.let { transform(it.first) to it.second }
}

fun <A : Any, B : Any> Parser<A>.andThen(aToParserB: (A) -> Parser<B>): Parser<B> = { input ->
    this(input)?.let { a -> aToParserB(a.first)(a.second) }
}

fun <A : Any, B : Any> Parser<A>.keep(parserB: Parser<B>): Parser<B> = { input ->
    this(input)?.let { resultA -> parserB(resultA.second) }
}

fun <A : Any, B : Any> Parser<A>.skip(parserB: Parser<B>): Parser<A> = { input ->
    this(input)?.let { resultA -> parserB(resultA.second)?.let { resultB -> resultA.first to resultB.second } }
}

fun <A:Any> oneOf(vararg parsers : Parser<A>) : Parser<A> = { input ->
    parsers.firstNotNullOfOrNull { it(input) }
}

fun <A:Any> lazy(parser : () -> Parser<A>) : Parser<A> = { input ->
    parser()(input)
}

operator fun Parser<String>.plus(parserB : Parser<String>) : Parser<String> = 
    this.andThen { valueA -> parserB.map {valueB -> valueA + valueB }}

// primitive parsers

fun s(str : String): Parser<String> =  { input ->
    when {
        input.startsWith(str) -> str to input.drop(str.length)
        else -> null
    }
}

fun match(test: (Char) -> Boolean) : Parser<Char> = { input -> 
    input.firstOrNull()?.let { 
        when {
            test(it) -> input.first() to input.drop(1)
            else -> null
        }
    }
}


val ws: Parser<String> = oneOf(
    s("\u0020") + _ws(),
    s("\u000A") + _ws(),
    s("\u000D") + _ws(),
    s("\u0009") + _ws(),
    succeed("")
)

fun _ws() : Parser<String> = lazy {ws} 



// A parser that always succeeds with a value without consuming any input
fun <T:Any> succeed(value : T) : Parser<T> = { input -> value to input }

val onenine : Parser<String> =
    match { it in '1' .. '9'}.map { it.toString()}

val digit : Parser<String> = oneOf(
    s("0"),
    onenine
)

val digits : Parser<String> = oneOf(
    digit + _digits(),
    digit
)

fun _digits() = lazy { digits }

val integer : Parser<String> = oneOf(
    onenine + digits,
    digit,
    s("-") + onenine + digits,
    s("-") + digits,
)

val fraction : Parser<String> = oneOf(
    s(".") + digits,
    succeed("")
)
val sign : Parser<String> = oneOf(
    s("+"),
    s("-"),
    succeed("")
)

val exponent : Parser<String> = oneOf(
    s("E") + sign + digits,
    s("e") + sign + digits,
    succeed("")
)


val number : Parser<String> = 
    integer + fraction + exponent



val hex : Parser<String> = oneOf(
    digit,
    match { it in 'A'..'F' }.map {it.toString()},
    match { it in 'a'..'f' }.map {it.toString()},
)

val escape : Parser<String> = oneOf(
    s("\"").map {"\""},
    s("\\").map {"\\"},
    s("/").map {"/"},
    s("b").map {"\b"},
    s("f").map {"\u000C"},
    s("n").map {"\n"},
    s("r").map {"\r"},
    s("t").map {"\t"},
    (s("u").map {""} + hex + hex + hex + hex).map {
        String.format("%c", it.toInt(16))
    }
)

val character : Parser<String> = oneOf(
    match { it >= '\u0020' && it != '\"' && it != '\\'}.map { it.toString() },  // "
    s("\\").keep(escape)
)

val characters : Parser<String> = oneOf(
    character + _characters(),
    succeed("")
)
fun _characters() = lazy { characters }

val string : Parser<String> =
    s("\"").keep(characters).skip(s("\""))
 

// JSON PARSER

sealed interface JsonValue {
    data object Null: JsonValue
    data class Bool(val value: Boolean): JsonValue
    data class Str(val value: String): JsonValue
    data class Num(val value: Double): JsonValue
    data class Array(val items: List<JsonValue>): JsonValue
    data class Object(val members: Map<String, JsonValue>): JsonValue
}

val jsBool: Parser<JsonValue> = oneOf(
    s("true").map { JsonValue.Bool(true) },
    s("false").map { JsonValue.Bool(false) }
    )

val jsNull: Parser<JsonValue> = 
    s("null").map { JsonValue.Null }

val jsNum: Parser<JsonValue> = 
    number.map { JsonValue.Num(it.toDouble()) }

val jsString: Parser<JsonValue> = 
    string.map { JsonValue.Str(it)}

val jsArray: Parser<JsonValue> = oneOf(    
    s("[").andThen { ws }.andThen {s("]")}.map {JsonValue.Array(emptyList<JsonValue>())},
    s("[").keep(_elements()).skip(s("]")).map {JsonValue.Array(it)},
)

val element : Parser<JsonValue> = 
    ws.keep(_jsonValue()).skip(ws)


val elements : Parser<List<JsonValue>> = oneOf(
    element.skip(s(",")).andThen { first -> _elements().map { rest -> 
        listOf(first) + rest
    } },
    element.map { listOf(it)},
)

fun _elements() : Parser<List<JsonValue>> = lazy {elements}

val member : Parser<Pair<String,JsonValue>> = 
    ws.keep(string).skip(ws).skip(s(":")).andThen { key -> element.map { value -> 
        key to value
    }}

val members : Parser<List<Pair<String,JsonValue>>> = oneOf(
    member.skip(s(",")).andThen { first -> _members().map { rest ->
        listOf(first) + rest
    }},
    member.map { listOf(it)}
)
fun _members() : Parser<List<Pair<String,JsonValue>>> = members

val jsObject : Parser<JsonValue> = oneOf(
    s("{").keep(members).skip(s("}")).map { JsonValue.Object(it.toMap())},
    s("{").skip(ws).keep(s("}")).map {JsonValue.Object(emptyMap())},
)

val jsonValue : Parser<JsonValue> = oneOf(
    jsNull,
    jsBool,
    jsString,
    jsNum,
    jsArray,
    jsObject
    )
fun _jsonValue() = lazy {jsonValue}


val json : Parser<JsonValue> = element



fun <A : Any> Parser<A>.runParser(input: String): A? = 
    this(input)?.let { 
        when (it.second) {
            "" -> it.first
            else -> null // it's an error if not all input is consumed
        }        
    }



fun JsonValue.str() : String = 
    (this as JsonValue.Str).value

fun JsonValue.num() : Double = 
    (this as JsonValue.Num).value

fun JsonValue.array() : List<JsonValue> = 
    (this as JsonValue.Array).items

fun JsonValue.field(field: String) : JsonValue = 
    (this as JsonValue.Object).members.get(field)!!