package konnik.json.result

/**
 * This is a small sum type that encodes success and failure of a computation.
 *
 * Inspired by the [Result type in Elm](https://package.elm-lang.org/packages/elm/core/latest/Result).
 */
sealed interface Result<out T>

/**
 * The result of a computation that succeeded.
 */
data class Ok<out T>(val value: T) : Result<T>

/**
 * The result of a computation that failed.
 */
data class Err(val error: String) : Result<Nothing>


/**
 * Maps a successful value using the provided [transform] function.
 */
fun <A, B> Result<A>.map(transform: (A) -> B): Result<B> =
    when (this) {
        is Err -> this
        is Ok -> Ok(transform(this.value))
    }

/**
 * Combines two successful values using the provided [transform] function.
 */
fun <A, B, R> map2(a: Result<A>, b: Result<B>, transform: (A, B) -> R): Result<R> =
    a.andThen { aValue -> b.map { bValue -> transform(aValue, bValue) } }

/**
 * Combines three successful values using the provided [transform] function.
 */
fun <A, B, C, R> map3(a: Result<A>, b: Result<B>, c: Result<C>, transform: (A, B, C) -> R): Result<R> =
    a.andThen { aValue -> map2(b, c) { bValue, cValue -> transform(aValue, bValue, cValue) } }

/**
 * Sequence two results where the second depends on the successful value first.
 */
fun <A, B> Result<A>.andThen(aToResultB: (A) -> Result<B>) =
    when (this) {
        is Err -> this
        is Ok -> aToResultB(this.value)
    }

/**
 * Combine a list of results into a result of a list.
 *
 * Will fail if any of the results in the list is an [Err]. The
 * resulting error will the error of the first [Err] in the list.
 */
fun <T> List<Result<T>>.combine(): Result<List<T>> =
    this.fold(Ok(emptyList())) { acc, item ->
        map2(acc, item) { a, b ->
            a + b
        }
    }
