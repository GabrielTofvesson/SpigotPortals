import java.util.*

class SortedList<E> private constructor(
    private val underlying: MutableList<E>,
    private val comparator: Comparator<in E>
): MutableList<E> by underlying {

    companion object {
        fun <T> create(
            type: Class<T>,
            underlying: MutableList<T>,
            comparator: Comparator<in T>
        ) = SortedList(Collections.checkedList(underlying, type), comparator)

        inline fun <reified T: Comparable<T>> create(
            underlying: MutableList<T> = ArrayList(),
            comparator: Comparator<T> = Comparator { a, b -> a.compareTo(b) }
        ) = create(T::class.java, underlying, comparator)

        inline fun <reified T> create(comparator: Comparator<T>, underlying: MutableList<T> = ArrayList()) =
            create(T::class.java, underlying, comparator)
    }

    init {
        if (underlying.size > 0)
            underlying.sortWith(comparator)
    }

    override fun add(element: E): Boolean {
        val index = underlying.binarySearch(element, comparator)

        if (index < 0)
            underlying.add(-(index + 1), element)
        else
            underlying[index] = element

        return index < 0
    }

    override fun add(index: Int, element: E) =
        throw UnsupportedOperationException("Cannot insert at index for sorted list")

    override fun addAll(elements: Collection<E>): Boolean {
        var result = false

        for (element in elements)
            result = add(element) || result

        return result
    }

    override fun addAll(index: Int, elements: Collection<E>) =
        throw UnsupportedOperationException("Cannot insert at index for sorted list")

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

    override fun removeAt(index: Int) =
        underlying.removeAt(index)

    override fun removeAll(elements: Collection<E>): Boolean {
        var result = false

        for (element in elements)
            result = remove(element) || result

        return result
    }
}