package konnik.json.result

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResultKtTest {

    @Test
    fun `map - ok and error`() {
        assertEquals(Ok("314"), Ok(314).map { it.toString() })
        assertEquals(Err("error"), Err("error").map { 123 })
    }

    @Test
    fun `map2 - ok and error`() {
        val err1: Result<Int> = Err("error1")
        val err2: Result<Int> = Err("error2")

        assertEquals(Ok(42), map(Ok(12), Ok(30)) { a, b -> a + b })
        assertEquals(err1, map(err1, Ok(30)) { a, b -> a + b })
        assertEquals(err2, map(Ok(12), err2) { a, b -> a + b })
    }

    @Test
    fun `map3 - ok and error`() {
        val err1: Result<Int> = Err("error1")
        val err2: Result<Int> = Err("error2")
        val err3: Result<Int> = Err("error3")

        assertEquals(Ok(42), map(Ok(12), Ok(10), Ok(20)) { a, b, c -> a + b + c })
        assertEquals(err1, map(err1, Ok(10), Ok(20)) { a, b, c -> a + b + c })
        assertEquals(err2, map(Ok(12), err2, Ok(20)) { a, b, c -> a + b + c })
        assertEquals(err3, map(Ok(12), Ok(10), err3) { a, b, c -> a + b + c })
        assertEquals(err1, map(err1, err2, err3) { a, b, c -> a + b + c })
    }

    @Test
    fun `andThen - chain two results`() {
        assertEquals(Ok(42), Ok(12).andThen { a -> Ok(a + 30) })
        assertEquals(Err("error2"), Ok(12).andThen { Err("error2") })
        assertEquals(Err("error1"), Err("error1").andThen { Ok(42) })
        assertEquals(Err("error1"), Err("error1").andThen { Err("error2") })
    }

    @Test
    fun combine() {
        val a = Ok(1)
        val b = Ok(2)
        val c = Ok(3)
        val err1 = Err("error1")
        val err2 = Err("error2")

        assertEquals(Ok(listOf(1, 2, 3)), listOf(a, b, c).combine())
        assertEquals(Err("error1"), listOf(a, err1, c).combine())
        assertEquals(Err("error1"), listOf(err1, err2).combine())
    }
}