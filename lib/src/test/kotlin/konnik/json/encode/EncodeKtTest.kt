package konnik.json.encode

import konnik.json.parser.parseJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EncodeKtTest {

    @Test
    fun `object builder`() {
        val myObj = obj {
            "a" to "Sausage"
            "b" to 3.14
            "c" to 314
            "d" to true
            "e" to nullValue()
            "f" to array("one", "two", "three")
            "g" to obj {
                "inner" to "Wow!"
            }
            "h" to array(str("a string"), number(42), bool(false))
        }

        val expectedValue = parseJson(
            """
            {
                "a" : "Sausage",
                "b" : 3.14,
                "c" : 314,
                "d" : true,
                "e" : null,
                "f" : ["one", "two", "three"],
                "g" : {
                    "inner" : "Wow!"
                },
                "h" :["a string", 42, false]
            }            
        """.trimIndent()
        ) ?: throw IllegalArgumentException("Not valid json")

        assertEquals(expectedValue, myObj)
    }

    @Test
    fun `encodeJson parseJson round trip`() {
        val myObj = obj {
            "a" to "Sausage"
            "b" to 3.14E-99
            "c" to -314
            "d" to true
            "e" to nullValue()
            "f" to array("one", "two", "three")
            "g" to obj {
                "inner" to "JSON is fantastic!!!"
            }
            "h" to array(str("a string"), number(42), bool(false), nullValue())
            "i" to "escaped\"\\\t\n\b\r\u000Cstring"
            "j" to "unicode: ©\uD83E\uDD21"
        }

        val json = encodeJson(myObj)
        val myObj2 = parseJson(json)

        assertEquals(myObj, myObj2)

    }
}