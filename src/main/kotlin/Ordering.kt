fun <T> T.compareByOrder(other: T, vararg comparables: T.() -> Comparable<*>): Int {
    for (comparable in comparables) {
        @Suppress("UNCHECKED_CAST")
        val result = (comparable(this) as Comparable<Any?>).compareTo(comparable(other))

        if (result != 0)
            return result
    }

    return 0
}

fun compareValues(vararg pairs: Pair<() -> Comparable<*>, () -> Comparable<*>>): Int {
    for ((a, b) in pairs) {
        val result = (a() as Comparable<Any?>).compareTo(b())

        if (result != 0)
            return result
    }

    return 0
}