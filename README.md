# Kotlin JSON parser (from scratch)

UPDATE: now also includes some nice [decoder combinators](#now-with-decoder-combinators)

[![Java CI with Gradle](https://github.com/konnik/kotlin-json-parser/actions/workflows/gradle.yml/badge.svg)](https://github.com/konnik/kotlin-json-parser/actions/workflows/gradle.yml)

This is a tiny but fully functional JSON parser I hacked together in Kotlin in a
day just for the fun of it. One of the inspirations was Graham Hutton's nice introduction
to [functional parsing](https://www.youtube.com/watch?v=dDtZLm7HIJs) on YouTube.

My main goal was to build a fp-style recursive descent parser exploring
Kotlin's support for functional programming techniques and I do like
how it turned out. I think the code is pretty readable and the different parsers for the
grammar reads almost exactly as the corresponding grammar rules on [www.json.org](https://www.json.org/).

Note that I have not made any effort on making it perform well
and I haven't benchmarked it at all. It's probably pretty slow, it's just a
toy project after all...

The source is in a single file and under 200 lines (not counting comments and blanks)
and can easily be copied into your own project if you want to use it.

Suggestions are welcome if you think something can be done in a better way.

Please also checkout my other JSON parsers written in [Haskell](https://github.com/konnik/haskell-json-parser/)
and [Clojure](https://github.com/konnik/clojure-json-parser/).

Happy parsing!

## Now with decoder combinators

After finishing the parser I really wanted to have a go at implementing something
similar to the [Elm JSON Decode package](https://package.elm-lang.org/packages/elm/json/latest/Json.Decode).

I really like the Elm way of decoding JSON by describing the expected data structure using
decoder combinators and mapping the data to the applications domain model.

My implementation have almost all the features of the Elm package and I think it turned out to
be quite nice.

For example consider the following two JSON objects describing two different types of users, guests and registered
users:

Guest:

```json
{
  "type": "guest",
  "displayName": "Guest123"
}
```

Registered user:

```json
// registeredJson
{
  "type": "registered",
  "id": 42,
  "alias": "mrsmith",
  "email": "mrsmith@example.com",
  "phone": null
}
```

Let's say we want to decode users into the following data structure:

```kotlin
sealed interface User {
    data class Guest(val displayName: String) : User
    data class Registered(val id: Int, val alias: String, val email: String, val phone: String?) : User
}
```

One way to define a decoder for `User` objects would be like this:

```kotlin
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
```

This decoder can now be used to parse and decode the users using the `decodeJson` function:

```kotlin
val guest = decodeJson(guestJson, userDecoder)
println(guest)

val registered = decodeJson(registeredJson, userDecoder)
println(registered)
```

This will render the output:

```text
Ok(value=Guest(displayName=Guest123))
Ok(value=Registered(id=42, alias=mrsmith, email=mrsmith@example.com, phone=null))
```

Note that I had to implement a simple `Result` type too, of course also heavily inspired
by [the Result type in Elm](https://package.elm-lang.org/packages/elm/core/latest/Result).

