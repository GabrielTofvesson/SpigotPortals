import java.util.*

class MultiSortedList<E> constructor(
    underlying: MutableList<E>,
    generator: () -> MutableList<E>,
    comparator: Comparator<in E>,
    vararg extraComparators: Comparator<in E>
): SortedList<E>(underlying, comparator) {
    companion object {
        fun <T: Comparable<T>> ofComparable(
            underlying: MutableList<T>,
            generator: () -> MutableList<T> = ::ArrayList,
            comparator: Comparator<in T> = Comparator { a, b -> a.compareTo(b) },
            vararg extraComparators: Comparator<in T>
        ) = MultiSortedList(underlying, generator, comparator, *extraComparators)

        fun <T: Comparable<T>> ofComparable(
            generator: () -> MutableList<T> = ::ArrayList,
            comparator: Comparator<in T> = Comparator { a, b -> a.compareTo(b) },
            vararg extraComparators: Comparator<in T>
        ) = MultiSortedList(generator(), generator, comparator, *extraComparators)
    }

    constructor(generator: () -> MutableList<E>, comparator: Comparator<in E>, vararg extraComparators: Comparator<in E>):
            this(generator(), generator, comparator, *extraComparators)

    private var extraLists = extraComparators.associateWith {
        val list = generator()
        list.addAll(underlying)
        SortedList(list, it)
    }

    override fun add(element: E) = add(element, true)
    override fun add(element: E, searchForward: Boolean): Boolean {
        extraLists.values.forEach {
            it.add(element, searchForward)
        }

        return super.add(element, searchForward)
    }

    override fun add(index: Int, element: E) = add(index, element, true)
    override fun add(index: Int, element: E, searchForward: Boolean) {
        add(element, searchForward)
    }

    override fun addAll(elements: Collection<E>) = addAll(elements, true)
    override fun addAll(elements: Collection<E>, searchForward: Boolean): Boolean {
        for (element in elements)
            add(element, searchForward)

        return elements.isNotEmpty()
    }

    override fun addAll(index: Int, elements: Collection<E>) = addAll(index, elements, true)
    override fun addAll(index: Int, elements: Collection<E>, searchForward: Boolean) = addAll(elements, searchForward)

    fun contains(element: E, comparator: Comparator<E>, searchForward: Boolean = true) =
        if (comparator == this.comparator) super.contains(element, searchForward)
        else extraLists[comparator]!!.contains(element, searchForward)


    override fun remove(element: E) = remove(element, true)
    override fun remove(element: E, searchForward: Boolean): Boolean {
        if (super.remove(element, searchForward)) {
            extraLists.values.forEach {
                it.remove(element, searchForward)
            }

            return true
        }

        return false
    }

    override fun removeAll(elements: Collection<E>) = removeAll(elements, true)
    override fun removeAll(elements: Collection<E>, searchForward: Boolean): Boolean {
        var result = false

        for (element in elements)
            result = remove(element, searchForward) || result

        return result
    }


    override fun removeAt(index: Int) = removeAt(index, comparator)
    fun removeAt(index: Int, comparator: Comparator<in E>, searchForward: Boolean = true): E {
        if (comparator == this.comparator) {
            val result = super.removeAt(index)

            extraLists.values.forEach {
                it.remove(result, searchForward)
            }

            return result
        } else {
            val result = extraLists[comparator]!!.removeAt(index)

            extraLists.forEach { (comp, list) -> if (comparator != comp) list.remove(result, searchForward) }
            super.remove(result, searchForward)

            return result
        }
    }

    fun get(index: Int, comparator: Comparator<in E>) =
        if (comparator == this.comparator) this[index]
        else extraLists[comparator]!![index]

    /**
     * Get all contiguous elements that match a comparison
     * @return Iterable object of all matching elements, or null if none exist
     */
    fun getAll(comparator: Comparator<in E>, findBase: (E) -> Int, compare: (E) -> Int): Iterable<E>? {
        var index = search(comparator, findBase)
        if (index < 0) {
            index = -(index + 1)

            if (index >= size || compare(get(index, comparator)) != 0)
                return null
        }

        // This should help with accessing entries in sequential collections (e.g. linked lists)
        val iterator = (extraLists[comparator] ?: this).subList(index, size).iterator()

        val result = LinkedList<E>()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (compare(element) != 0)
                break

            result += element
        }

        return result
    }

    fun getAll(comparator: Comparator<in E>, comparison: (E) -> Int) = getAll(comparator, comparison, comparison)

    fun findValueOrNull(comparator: Comparator<in E>, comparison: (E) -> Int): E? {
        val index = search(comparator, comparison)

        if (index < 0) return null

        return get(index, comparator)
    }

    fun search(element: E, comparator: Comparator<in E>, searchStartToEnd: Boolean = true) =
        if (comparator == this.comparator) search(element, searchStartToEnd)
        else extraLists[comparator]!!.search(element, searchStartToEnd)

    fun search(comparator: Comparator<in E>, comparison: (E) -> Int) =
        if (comparator == this.comparator) search(comparison)
        else extraLists[comparator]!!.search(comparison)
}
