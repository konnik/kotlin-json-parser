package konnik.json

/**
 * Transforms the value of a successful parse to another value.
 *
 * Fun fact: this means that Parser is a Functor and corresponds to fmap in Haskell.
 */
fun <A : Any, B : Any> Parser<A>.map(transform: (A) -> B): Parser<B> = { input ->
    this(input)?.let { transform(it.first) to it.second }
}

/**
 * A parser that always succeeds with the provided value without consuming any input.
 *
 * Fun fact: this corresponds to pure (from type class Applicative) in Haskell.
 */

fun <T : Any> succeed(value: T): Parser<T> = { input -> value to input }

/**
 * Chain two parsers together where the second parser depends on the produced value
 * if the first.
 *
 * This function is often called flatMap, but I think andThen is a more intuitive name. It's
 * also the name used in the Elm, the best programming language ever created so let's go with that.
 *
 * Fun fact: This is bind / >>= in Haskell and makes our Parser a Monad.
 */
fun <A : Any, B : Any> Parser<A>.andThen(aToParserB: (A) -> Parser<B>): Parser<B> = { input ->
    this(input)?.let { a -> aToParserB(a.first)(a.second) }
}

/**
 * Chain two parsers together but keep only the value from de second parser.
 *
 * Fun fact: This is Applicative *> (or Monad >>) in Haskell
 */
fun <A : Any, B : Any> Parser<A>.keep(parserB: Parser<B>): Parser<B> =
    this.andThen { parserB }


/**
 * Chain two parsers together but keep only the value from de first parser.
 *
 * Fun fact: This is Applicative <* in Haskell
 */
fun <A : Any, B : Any> Parser<A>.skip(parserB: Parser<B>): Parser<A> =
    this.andThen { a -> parserB.map { a } }

/**
 * Combine multiple parsers into a parser that returns the value of the first
 * parser that succeeds.
 */
fun <A : Any> oneOf(vararg parsers: Parser<A>): Parser<A> = { input ->
    parsers.firstNotNullOfOrNull { it(input) }
}

/**
 * Make the construction of a parser lazy. This is sometimes useful for
 * preventing stack overflows when defining parsers recursively.
 */
fun <A : Any> lazy(parser: () -> Parser<A>): Parser<A> = { input ->
    parser()(input)
}

/**
 * Combine to parsers into one that concatenates the string results of the
 * individual parsers into one string.
 */
operator fun Parser<String>.plus(parserB: Parser<String>): Parser<String> =
    this.andThen { valueA -> parserB.map { valueB -> valueA + valueB } }
