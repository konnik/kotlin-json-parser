# Kotlin JSON parser (from scratch)

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

