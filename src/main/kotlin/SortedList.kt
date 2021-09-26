import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.RandomAccess

open class SortedList<E> constructor(
    private val underlying: MutableList<E> = ArrayList(),
    val comparator: Comparator<in E>
): MutableList<E> by underlying {
    companion object {
        fun <T: Comparable<T>> ofComparable(
            underlying: MutableList<T> = ArrayList(),
            comparator: Comparator<T> = Comparator { a, b -> a.compareTo(b) }
        ) = SortedList(underlying, comparator)
    }

    init {
        if (underlying.size > 0)
            underlying.sortWith(comparator)
    }

    open fun add(element: E, searchForward: Boolean): Boolean {
        val index = search(element, searchForward)

        underlying.add(if (index < 0) -(index + 1) else index, element)

        return true
    }

    override fun add(element: E) = add(element, true)

    open fun add(index: Int, element: E, searchForward: Boolean) {
        add(element, searchForward)
    }
    override fun add(index: Int, element: E) = add(index, element, true)

    override fun addAll(elements: Collection<E>): Boolean = addAll(elements, true)
    open fun addAll(elements: Collection<E>, searchForward: Boolean): Boolean {
        for (element in elements)
            add(element, searchForward)

        return elements.isNotEmpty()
    }

    open fun addAll(index: Int, elements: Collection<E>, searchForward: Boolean) = addAll(elements, searchForward)
    override fun addAll(index: Int, elements: Collection<E>) = addAll(index, elements, true)
    open fun contains(element: E, searchForward: Boolean) = search(element, searchForward) >= 0
    override fun contains(element: E) = contains(element, true)

    override fun indexOf(element: E) = search(element, true)
    override fun lastIndexOf(element: E) = search(element, false)

    override fun containsAll(elements: Collection<E>) = containsAll(elements, true)
    open fun containsAll(elements: Collection<E>, searchForward: Boolean): Boolean {
        for (element in elements)
            if (!contains(element, searchForward))
                return false

        return true
    }

    override fun remove(element: E) = remove(element, true)
    open fun remove(element: E, searchForward: Boolean): Boolean {
        val index = search(element, searchForward)

        if (index >= 0) {
            underlying.removeAt(index)
            return true
        }

        return false
    }

    override fun removeAt(index: Int) = underlying.removeAt(index)
    override fun removeAll(elements: Collection<E>) = removeAll(elements, true)
    open fun removeAll(elements: Collection<E>, searchForward: Boolean): Boolean {
        var result = false

        for (element in elements)
            result = remove(element, searchForward) || result

        return result
    }

    fun isRandomAccess() = underlying is RandomAccess

    fun search(element: E, searchStartToEnd: Boolean = true) =
        if (isRandomAccess()) binarySearch(element, comparator)
        else {
            // Sequential search, because it's probably faster than binary search
            val index = if (searchStartToEnd) indexOfFirst { comparator.compare(element, it) >= 0 }
            else indexOfLast { comparator.compare(element, it) <= 0 }

            if (index < 0 && searchStartToEnd) -size
            else if (index < 0) -1
            else if (comparator.compare(element, this[index]) == 0) index
            else -(index + 1)
        }

    fun search(comparison: Comparison<E>, searchStartToEnd: Boolean = true) =
        if (isRandomAccess()) binarySearch(comparison = comparison)
        else {
            // Sequential search, because it's probably faster than binary search
            val index = if (searchStartToEnd) indexOfFirst { comparison(it) >= 0 }
            else indexOfLast { comparison(it) <= 0 }

            if (index < 0 && searchStartToEnd) -size
            else if (index < 0) -1
            else if (comparison(this[index]) == 0) index
            else -(index + 1)
        }
}