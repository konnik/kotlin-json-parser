#!/usr/bin/env kotlin

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

// PARSE SOMETHING WITH OUR SHINY NEW PARSER


val TESTJSON =     """
    {"hej"   : "apa\t\u00A9"
    , "x" : [1,2,3,false,true,null],
    "y" : null
    }
    """

val FORSJSON = """
{"forsstracka":[{"id":1,"namn":"Brännsågen-Åbyggeby","gradering":{"klass":"2","lyft":[]},"langd":6000,"fallhojd":28,"koordinater":{"lat":60.75627,"long":17.03825},"flode":{"smhipunkt":12020,"minimum":20,"optimal":30,"maximum":100},"vattendrag":[{"id":1,"namn":"Testeboån"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":2,"namn":"Forsby","gradering":{"klass":"2","lyft":[]},"langd":250,"fallhojd":4,"koordinater":{"lat":60.71786,"long":17.14122},"flode":{"smhipunkt":12020,"minimum":31,"optimal":50,"maximum":100},"vattendrag":[{"id":1,"namn":"Testeboån"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":6,"namn":"Gysinge","gradering":{"klass":"2","lyft":[]},"langd":300,"fallhojd":2,"koordinater":{"lat":60.2861,"long":16.88485},"flode":{"smhipunkt":41214,"minimum":100,"optimal":300,"maximum":1000},"vattendrag":[{"id":122,"namn":"Dalälven"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":7,"namn":"Bresiljeån Nedre turen","gradering":{"klass":"3","lyft":[]},"langd":5000,"fallhojd":54,"koordinater":{"lat":60.97895,"long":16.41164},"flode":{"smhipunkt":12741,"minimum":10,"optimal":15,"maximum":20},"vattendrag":[{"id":32,"namn":"Bresiljeån"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":4,"namn":"Konserthuset","gradering":{"klass":"2","lyft":["5","4+"]},"langd":1000,"fallhojd":5,"koordinater":{"lat":60.67282158658722,"long":17.13363436915774},"flode":{"smhipunkt":11802,"minimum":30,"optimal":60,"maximum":100},"vattendrag":[{"id":2,"namn":"Gavleån"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":3,"namn":"Vävaren","gradering":{"klass":"3+","lyft":[]},"langd":350,"fallhojd":12,"koordinater":{"lat":60.70092,"long":17.15604},"flode":{"smhipunkt":12020,"minimum":20,"optimal":25,"maximum":40},"vattendrag":[{"id":1,"namn":"Testeboån"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":8,"namn":"Kölsjöån","gradering":{"klass":"3","lyft":[]},"langd":4500,"fallhojd":61,"koordinater":{"lat":60.94998,"long":16.41111},"flode":{"smhipunkt":12682,"minimum":10,"optimal":15,"maximum":20},"vattendrag":[{"id":131,"namn":"Kölsjöån"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":9,"namn":"Mellanturen","gradering":{"klass":"4","lyft":["5"]},"langd":3500,"fallhojd":65,"koordinater":{"lat":61.94755,"long":15.22474},"flode":{"smhipunkt":15868,"minimum":8,"optimal":12,"maximum":20},"vattendrag":[{"id":132,"namn":"Ängraån"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":10,"namn":"Övre turen","gradering":{"klass":"3","lyft":[]},"langd":4000,"fallhojd":30,"koordinater":{"lat":61.97015,"long":15.07083},"flode":{"smhipunkt":15868,"minimum":8,"optimal":12,"maximum":20},"vattendrag":[{"id":132,"namn":"Ängraån"}],"lan":[{"id":21,"namn":"Gävleborg"}]},{"id":11,"namn":"Mossidammen-Sunnet","gradering":{"klass":"3","lyft":["3+"]},"langd":5000,"fallhojd":50,"koordinater":{"lat":61.53897,"long":13.9347},"flode":{"smhipunkt":14527,"minimum":10,"optimal":20,"maximum":30},"vattendrag":[{"id":133,"namn":"Rotnen"}],"lan":[{"id":20,"namn":"Dalarna"}]},{"id":12,"namn":"Övre turen","gradering":{"klass":"3","lyft":["4"]},"langd":5000,"fallhojd":5,"koordinater":{"lat":61.37713,"long":14.63739},"flode":{"smhipunkt":13771,"minimum":10,"optimal":15,"maximum":30},"vattendrag":[{"id":134,"namn":"Ämån"}],"lan":[{"id":20,"namn":"Dalarna"}]},{"id":13,"namn":"Övre övre","gradering":{"klass":"3","lyft":["1"]},"langd":8,"fallhojd":45,"koordinater":{"lat":61.4402,"long":14.51295},"flode":{"smhipunkt":13771,"minimum":15,"optimal":30,"maximum":50},"vattendrag":[{"id":134,"namn":"Ämån"}],"lan":[{"id":20,"namn":"Dalarna"}]},{"id":14,"namn":"Övre Ljusnan","gradering":{"klass":"4","lyft":[]},"langd":3000,"fallhojd":35,"koordinater":{"lat":62.65431,"long":12.42141},"flode":{"smhipunkt":18404,"minimum":10,"optimal":20,"maximum":30},"vattendrag":[{"id":129,"namn":"Ljusnan"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":15,"namn":"Övre","gradering":{"klass":"4","lyft":[]},"langd":4000,"fallhojd":105,"koordinater":{"lat":63.10441,"long":13.8347},"flode":{"smhipunkt":20273,"minimum":20,"optimal":30,"maximum":40},"vattendrag":[{"id":126,"namn":"Dammån"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":16,"namn":"Normalturen","gradering":{"klass":"2","lyft":["3"]},"langd":5000,"fallhojd":60,"koordinater":{"lat":63.16546,"long":13.97987},"flode":{"smhipunkt":20378,"minimum":20,"optimal":40,"maximum":60},"vattendrag":[{"id":126,"namn":"Dammån"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":17,"namn":"Gräftån","gradering":{"klass":"3","lyft":["4","5"]},"langd":9000,"fallhojd":194,"koordinater":{"lat":63.04225,"long":13.96449},"flode":{"smhipunkt":20122,"minimum":10,"optimal":15,"maximum":30},"vattendrag":[{"id":136,"namn":"Gräftån"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":18,"namn":"Vålån - Nedre","gradering":{"klass":"4","lyft":["5"]},"langd":6400,"fallhojd":70,"koordinater":{"lat":63.16679,"long":13.07094},"flode":{"smhipunkt":20634,"minimum":15,"optimal":25,"maximum":50},"vattendrag":[{"id":3,"namn":"Vålån"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":19,"namn":"Holderströmmen","gradering":{"klass":"3","lyft":["4"]},"langd":2700,"fallhojd":24,"koordinater":{"lat":63.98487,"long":12.91461},"flode":{"smhipunkt":62660,"minimum":15,"optimal":24,"maximum":40},"vattendrag":[{"id":137,"namn":"Holderströmmen"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":20,"namn":"Gevsjöströmmarna","gradering":{"klass":"4","lyft":["5+"]},"langd":4800,"fallhojd":60,"koordinater":{"lat":63.41506,"long":12.63445},"flode":{"smhipunkt":21531,"minimum":30,"optimal":50,"maximum":100},"vattendrag":[{"id":34,"namn":"Indalsälven"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":21,"namn":"Medstugån","gradering":{"klass":"5","lyft":["5+"]},"langd":5600,"fallhojd":80,"koordinater":{"lat":63.47772,"long":12.54488},"flode":{"smhipunkt":21965,"minimum":10,"optimal":25,"maximum":40},"vattendrag":[{"id":138,"namn":"Medstugån"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":22,"namn":"Enan","gradering":{"klass":"4+","lyft":["5"]},"langd":6800,"fallhojd":80,"koordinater":{"lat":63.24103,"long":12.19648},"flode":{"smhipunkt":21003,"minimum":10,"optimal":20,"maximum":40},"vattendrag":[{"id":139,"namn":"Enan"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":23,"namn":"Gausta","gradering":{"klass":"5","lyft":[]},"langd":25000,"fallhojd":500,"koordinater":{"lat":64.99597,"long":14.2319},"flode":{"smhipunkt":62393,"minimum":10,"optimal":15,"maximum":20},"vattendrag":[{"id":140,"namn":"Gausta"}],"lan":[{"id":23,"namn":"Jämtland"}]},{"id":24,"namn":"Storån Normalturen","gradering":{"klass":"3","lyft":["4"]},"langd":6600,"fallhojd":54,"koordinater":{"lat":61.92749,"long":12.65889},"flode":{"smhipunkt":15945,"minimum":20,"optimal":40,"maximum":80},"vattendrag":[{"id":130,"namn":"Storån"}],"lan":[{"id":20,"namn":"Dalarna"}]}]}
"""

val result =
    json.runParser(TESTJSON)
println("'$result'" ?: "Parse failed")

// result!!.field("forsstracka").array().forEach { f ->
//     println(f.field("id").num().toString() + " => " + f.field("namn").str())
// }


fun JsonValue.str() : String = 
    (this as JsonValue.Str).value

fun JsonValue.num() : Double = 
    (this as JsonValue.Num).value

fun JsonValue.array() : List<JsonValue> = 
    (this as JsonValue.Array).items

fun JsonValue.field(field: String) : JsonValue = 
    (this as JsonValue.Object).members.get(field)!!