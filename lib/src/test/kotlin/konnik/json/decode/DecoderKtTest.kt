package konnik.json.decode

import konnik.json.parser.JsonValue
import konnik.json.result.Err
import konnik.json.result.Ok
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DecoderKtTest {

    @Test
    fun `int - ok`() {
        assertEquals(Ok(314), int(JsonValue.Num(314.0)))
    }

    @Test
    fun `int - fails`() {
        assertEquals(Err("3.14 is not an integer"), int(JsonValue.Num(3.14)))
        assertEquals(Err("null is not an integer"), int(JsonValue.Null))
        assertEquals(Err("\"Apa\" is not an integer"), int(JsonValue.Str("Apa")))
        assertEquals(Err("{} is not an integer"), int(JsonValue.Object(emptyMap())))
    }

    @Test
    fun `bool - ok`() {
        assertEquals(Ok(true), bool(JsonValue.Bool(true)))
        assertEquals(Ok(false), bool(JsonValue.Bool(false)))
    }

    @Test
    fun `bool - fails`() {
        assertEquals(Err("\"apa\" is not a boolean"), bool(JsonValue.Str("apa")))
    }

    @Test
    fun `nullable - ok`() {
        assertEquals(Ok(true), nullable(bool)(JsonValue.Bool(true)))
        assertEquals(Ok(null), nullable(bool)(JsonValue.Null))

        val obj = JsonValue.Object(
            mapOf(
                "a" to JsonValue.Num(3.14),
                "b" to JsonValue.Null,
            )
        )

        assertEquals(Ok(3.14), field("a", nullable(double))(obj))
        assertEquals(Ok(null), field("b", nullable(double))(obj))

    }

    @Test
    fun `field - ok`() {
        val obj = JsonValue.Object(
            mapOf(
                "a" to JsonValue.Num(314.0),
                "b" to JsonValue.Num(3.14),
                "c" to JsonValue.Str("apa")
            )
        )

        assertEquals(Ok(314), field("a", int)(obj))
        assertEquals(Ok(3.14), field("b", double)(obj))
        assertEquals(Ok("apa"), field("c", str)(obj))

    }

    @Test
    fun `field of`() {
        val obj = JsonValue.Object(
            mapOf(
                "a" to JsonValue.Num(314.0),
                "b" to JsonValue.Num(3.14),
                "c" to JsonValue.Str("apa")
            )
        )

        assertEquals(Ok(314), (field("a") of int)(obj))
        assertEquals(Ok(3.14), (field("b") of double)(obj))
        assertEquals(Ok("apa"), (field("c") of str)(obj))

    }

    @Test
    fun `object - at`() {
        val obj = JsonValue.Object(
            mapOf(
                "a" to JsonValue.Object(
                    mapOf(
                        "b" to JsonValue.Object(
                            mapOf(
                                "c" to JsonValue.Str("apa")
                            )
                        )
                    )
                ),
            )
        )

        assertEquals(Ok("apa"), (at(listOf("a", "b", "c"), str))(obj))

    }

    @Test
    fun `list - ok`() {
        val intArray = JsonValue.Array(
            listOf(JsonValue.Num(1.0), JsonValue.Num(2.0), JsonValue.Num(3.0))
        )

        val strArray = JsonValue.Array(
            listOf(JsonValue.Str("apa"), JsonValue.Str("korv"), JsonValue.Str("snabel"))
        )

        assertEquals(Ok(listOf(1, 2, 3)), list(int)(intArray))
        assertEquals(Ok(listOf("apa", "korv", "snabel")), list(str)(strArray))


    }

    @Test
    fun `list - fails`() {
        val intArray = JsonValue.Array(
            listOf(JsonValue.Num(1.0), JsonValue.Num(2.0), JsonValue.Num(3.0))
        )

        val strArray = JsonValue.Array(
            listOf(JsonValue.Str("apa"), JsonValue.Str("korv"), JsonValue.Str("snabel"))
        )


        assertEquals(Err("null is not an array"), list(int)(JsonValue.Null))
        assertEquals(Err("\"apa\" is not an integer"), list(int)(strArray))
        assertEquals(Err("1.0 is not a string"), list(str)(intArray))

    }


    @Test
    fun `succeed - always succeed`() {
        assertEquals(Ok("ok"), succeed("ok")(JsonValue.Null))
    }

    @Test
    fun `fail - always fails`() {
        assertEquals(Err("Failed"), fail<Nothing>("Failed")(JsonValue.Null))
    }

    @Test
    fun `map5 - map to data class`() {
        val obj = JsonValue.Object(
            mapOf(
                "a" to JsonValue.Num(314.0),
                "b" to JsonValue.Num(3.14),
                "c" to JsonValue.Str("apa"),
                "d" to JsonValue.Bool(true),
                "e" to JsonValue.Array(listOf(JsonValue.Str("item1"))),
            )
        )

        data class MyObj(
            val a: Int,
            val b: Double,
            val c: String,
            val d: Boolean,
            val e: List<String>
        )

        val myObjDecoder = map(
            field("a") of int,
            field("b") of double,
            field("c") of str,
            field("d") of bool,
            field("e") of (list of str),
            ::MyObj,
        )

        assertEquals(
            Ok(MyObj(314, 3.14, "apa", true, listOf("item1"))),
            myObjDecoder(obj)
        )
    }

    @Test
    fun `andThen - ok and errors`() {
        assertEquals(
            Ok(11),
            succeed(5).andThen { succeed(it + 6) }(JsonValue.Null)
        )
        assertEquals(
            Err("First failed"),
            fail<Int>("First failed").andThen { succeed(it + 6) }(JsonValue.Null)
        )
        assertEquals(
            Err("Second failed"),
            succeed(5).andThen { fail<Int>("Second failed") }(JsonValue.Null)
        )
        assertEquals(
            Err("First failed"),
            fail<Int>("First failed").andThen { fail<Int>("Second failed") }(JsonValue.Null)
        )

    }

    @Test
    fun `combine - list of decoders`() {
        val obj = JsonValue.Object(
            mapOf(
                "a" to JsonValue.Str("apa"),
                "b" to JsonValue.Num(3.14),
                "c" to JsonValue.Bool(true)
            )
        )

        val decoder = listOf(
            field("a") of str,
            field("b") of double,
            field("c") of bool,
        ).combine()

        val result = decoder(obj)

        assertEquals(Ok(listOf("apa", 3.14, true)), result)

    }

    @Test
    fun `oneOf - choose one of many parsers`() {
        val myDecoder = oneOf<Any>(
            int,
            bool,
            str,
        )

        assertEquals(Ok(314), myDecoder(JsonValue.Num(314.0)))
        assertEquals(Ok(true), myDecoder(JsonValue.Bool(true)))
        assertEquals(Ok("korv"), myDecoder(JsonValue.Str("korv")))
        assertEquals(Err("oneOf failed to decode null"), myDecoder(JsonValue.Null))
    }
}