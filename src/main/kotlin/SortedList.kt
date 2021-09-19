import java.util.*
import kotlin.collections.ArrayList

open class SortedList<E> constructor(
    private val underlying: MutableList<E> = ArrayList(),
    protected val comparator: Comparator<in E>
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

    override fun add(element: E): Boolean {
        val index = underlying.binarySearch(element, comparator)

        underlying.add(if (index < 0) -(index + 1) else index, element)

        return true
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
    override fun contains(element: E) = underlying.binarySearch(element, comparator) >= 0

    override fun containsAll(elements: Collection<E>): Boolean {
        for (element in elements)
            if (!contains(element))
                return false

        return true
    }

    override fun remove(element: E): Boolean {
        val index = underlying.binarySearch(element, comparator)

        if (index >= 0) {
            underlying.removeAt(index)
            return true
        }

        return false
    }

    override fun removeAt(index: Int) = underlying.removeAt(index)
    override fun removeAll(elements: Collection<E>): Boolean {
        var result = false

        for (element in elements)
            result = remove(element) || result

        return result
    }
}