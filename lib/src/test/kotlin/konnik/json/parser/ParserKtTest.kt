/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package konnik.json.parser

import konnik.json.JsonValue
import konnik.json.decode.*
import konnik.json.result.Ok
import kotlin.test.Test
import kotlin.test.assertEquals


class ParserKtTest {
    @Test
    fun `true`() {
        val result = parseJson("true")
        assertEquals(JsonValue.Bool(true), result)
    }

    @Test
    fun `false`() {
        val result = parseJson("false")
        assertEquals(JsonValue.Bool(false), result)
    }

    @Test
    fun `null`() {
        val result = parseJson("null")
        assertEquals(JsonValue.Null, result)
    }

    @Test
    fun `numbers - valid ones`() {
        assertEquals(JsonValue.Num(3.14), parseJson("3.14"))
        assertEquals(JsonValue.Num(314.0), parseJson("3.14E+2"))
        assertEquals(JsonValue.Num(314.0), parseJson("3.14e+2"))
        assertEquals(JsonValue.Num(0.00314), parseJson("3.14E-3"))
        assertEquals(JsonValue.Num(0.00314), parseJson("3.14e-3"))
        assertEquals(JsonValue.Num(3000.0), parseJson("3E3"))
        assertEquals(JsonValue.Num(0.042), parseJson("42e-3"))
        assertEquals(JsonValue.Num(0.0), parseJson("0E3"))
        assertEquals(JsonValue.Num(-0.0), parseJson("-0e-3"))
        assertEquals(JsonValue.Num(123.0), parseJson("123"))
        assertEquals(JsonValue.Num(1.0), parseJson("1"))
        assertEquals(JsonValue.Num(0.0), parseJson("0"))
        assertEquals(JsonValue.Num(-0.0), parseJson("-0"))
        assertEquals(JsonValue.Num(-0.123), parseJson("-0.123"))
        assertEquals(JsonValue.Num(-1.0), parseJson("-1"))
    }

    @Test
    fun `numbers - invalid ones`() {
        assertEquals(null, parseJson("0123")) // cant start with 0 if more than one digit
        assertEquals(null, parseJson("-0123")) // negative numbers with more than on digit can't start with 0
        assertEquals(null, parseJson(".123")) // must have number before decimal point
        assertEquals(null, parseJson("02.123")) // cant start with 0 if integer part is more than one digit
        assertEquals(null, parseJson("+12")) // positive numbers does not use prefix +
    }

    @Test
    fun `strings - valid ones`() {
        assertEquals(JsonValue.Str("hello"), parseJson(""""hello""""))
        assertEquals(JsonValue.Str("hello\n"), parseJson(""""hello\n""""))
        assertEquals(JsonValue.Str("hello\t"), parseJson(""""hello\t""""))
        assertEquals(JsonValue.Str("hello\b"), parseJson(""""hello\b""""))
        assertEquals(JsonValue.Str("hello\r"), parseJson(""""hello\r""""))
        assertEquals(JsonValue.Str("back\\slash"), parseJson(""""back\\slash""""))
        assertEquals(JsonValue.Str("forward/slash"), parseJson(""""forward\/slash""""))
        assertEquals(JsonValue.Str("a \"quoted\" word"), parseJson(""""a \"quoted\" word""""))
        assertEquals(JsonValue.Str(""), parseJson(""""""""))
        assertEquals(JsonValue.Str("Unicode char: ©"), parseJson(""""Unicode char: \u00A9""""))
    }

    @Test
    fun `strings - invalid ones`() {
        assertEquals(null, parseJson(""""string containing " an unescaped quote""""))
        assertEquals(null, parseJson(""""string without end quote"""))
    }

    @Test
    fun `empty array`() {
        val result = parseJson("[]")
        assertEquals(JsonValue.Array(emptyList()), result)
    }

    @Test
    fun `array with values`() {
        val result = parseJson(
            """
            [true,false,["string with comma,",42]]
            """
        )
        assertEquals(
            JsonValue.Array(
                listOf(
                    JsonValue.Bool(true),
                    JsonValue.Bool(false),
                    JsonValue.Array(
                        listOf(
                            JsonValue.Str("string with comma,"),
                            JsonValue.Num(42.0)
                        )
                    )
                )
            ),
            result
        )
    }

    @Test
    fun `empty object`() {
        val result = parseJson("{}")
        assertEquals(JsonValue.Object(emptyMap()), result)
    }

    @Test
    fun `object with members`() {
        val jsonObj = """
            { "a": true, "b":false, "c": null, "d" : [1,2,3], "e": 42, "f": {"x":true}
            }
        """.trimIndent()
        val result = parseJson(jsonObj)
        val expected = JsonValue.Object(
            mapOf(
                "a" to JsonValue.Bool(true),
                "b" to JsonValue.Bool(false),
                "c" to JsonValue.Null,
                "d" to JsonValue.Array(listOf(JsonValue.Num(1.0), JsonValue.Num(2.0), JsonValue.Num(3.0))),
                "e" to JsonValue.Num(42.0),
                "f" to JsonValue.Object(
                    mapOf(
                        "x" to JsonValue.Bool(true)
                    )
                )
            )

        )

        assertEquals(expected, result)
    }

    @Test
    fun `all characters must be consumed`() {
        assertEquals(null, parseJson("true crap"))
        assertEquals(null, parseJson("falsetrue"))
        assertEquals(null, parseJson("nullfalse"))
        assertEquals(null, parseJson("42.2false"))
    }

    @Test
    fun `decodeJson - parse and decode a sum type`() {
        val registeredJson = """
            { "type": "registered", 
              "id": 42,
              "alias": "mrsmith",
              "email": "mrsmith@example.com",
              "phone": null
            }
        """.trimIndent()

        val guestJson = """
            { "type": "guest", 
              "displayName":"Guest123"
            }
        """.trimIndent()

        val userDecoder: Decoder<User> =
            field("type", str).andThen { type ->
                when (type) {
                    "guest" -> map(
                        field("displayName") of str,
                        User::Guest
                    )

                    "registered" -> map(
                        field("id") of int,
                        field("alias") of str,
                        field("email") of str,
                        field("phone") of nullable(str),
                        User::Registered
                    )

                    else -> fail("Invalid type: $type")
                }
            }

        val guest = decodeJson(guestJson, userDecoder)
        println(guest)
        val registered = decodeJson(registeredJson, userDecoder)
        println(registered)

        assertEquals(Ok(User.Guest("Guest123")), guest)
        assertEquals(Ok(User.Registered(42, "mrsmith", "mrsmith@example.com", null)), registered)

    }

    sealed interface User {
        data class Guest(val displayName: String) : User
        data class Registered(val id: Int, val alias: String, val email: String, val phone: String?) : User
    }


}
