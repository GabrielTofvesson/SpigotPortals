import java.util.*
import kotlin.Comparator

class MultiSortedList<E> constructor(
    underlying: MutableList<E>,
    comparator: Comparator<in E>,
    vararg extraComparators: Comparator<in E>
): SortedList<E>(underlying, comparator) {
    companion object {
        fun <T: Comparable<T>> ofComparable(
            underlying: MutableList<T> = ArrayList(),
            comparator: Comparator<in T> = Comparator { a, b -> a.compareTo(b) },
            vararg extraComparators: Comparator<in T>
        ) = MultiSortedList(underlying, comparator, *extraComparators)
    }

    private var extraLists = extraComparators.associateWith { SortedList(underlying.subList(0, underlying.size), it) }

    override fun add(element: E): Boolean {
        extraLists.values.forEach {
            it.add(element)
        }

        return super.add(element)
    }

    override fun add(index: Int, element: E) {
        add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        for (element in elements)
            add(element)

        return elements.isNotEmpty()
    }

    override fun addAll(index: Int, elements: Collection<E>) = addAll(elements)
    override fun containsAll(elements: Collection<E>): Boolean {
        for (element in elements)
            if (!contains(element))
                return false

        return true
    }

    fun contains(element: E, comparator: Comparator<E>) =
        if (comparator == this.comparator) contains(element)
        else extraLists[comparator]!!.contains(element)

    override fun remove(element: E): Boolean {
        if (super.remove(element)) {
            extraLists.values.forEach {
                it.remove(element)
            }

            return true
        }

        return false
    }

    override fun removeAt(index: Int) = removeAt(index, comparator)

    override fun removeAll(elements: Collection<E>): Boolean {
        var result = false

        for (element in elements)
            result = remove(element) || result

        return result
    }

    fun removeAt(index: Int, comparator: Comparator<in E>): E {
        if (comparator == this.comparator) {
            val result = super.removeAt(index)

            extraLists.values.forEach {
                it.remove(result)
            }

            return result
        } else {
            val result = extraLists[comparator]!!.removeAt(index)

            extraLists.forEach { (comp, list) -> if (comparator != comp) list.remove(result) }
            super.remove(result)

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
    fun getAll(comparator: Comparator<in E>, comparison: (E) -> Int): Iterable<E>? {
        var index = binSearch(comparator, comparison)
        if (index < 0) return null

        val result = LinkedList<E>()

        var element: E
        do {
            element = get(index++, comparator)
            if (comparison(element) != 0)
                break

            result += element
        } while (index < size)

        return result
    }

    fun findValueOrNull(comparator: Comparator<in E>, comparison: (E) -> Int): E? {
        val index = binSearch(comparator, comparison)

        if (index < 0) return null

        return get(index, comparator)
    }

    fun binSearch(element: E, comparator: Comparator<in E>) =
        if (comparator == this.comparator) binarySearch(element, comparator)
        else extraLists[comparator]!!.binarySearch(element, comparator)

    fun binSearch(comparator: Comparator<in E>, comparison: (E) -> Int) =
        if (comparator == this.comparator) binarySearch(comparison = comparison)
        else extraLists[comparator]!!.binarySearch(comparison = comparison)
}
