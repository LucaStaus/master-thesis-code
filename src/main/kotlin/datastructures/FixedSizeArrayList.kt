package datastructures

class FixedSizeArrayList(private val maxSize: Int) : Collection<Int> {

    private val array = IntArray(maxSize)
    private var curSize = 0

    override val size: Int
        get() = curSize

    override fun isEmpty(): Boolean = curSize == 0

    override fun containsAll(elements: Collection<Int>): Boolean {
        for (e in elements) {
            if (!contains(e)) return false
        }
        return true
    }

    override fun contains(element: Int): Boolean {
        for (e in this) {
            if (e == element) return true
        }
        return false
    }

    fun getFirst(): Int {
        if (isEmpty()) throw NoSuchElementException()
        return array[0]
    }

    fun getLast(): Int {
        if (isEmpty()) throw NoSuchElementException()
        return array[curSize - 1]
    }

    operator fun get(i: Int): Int {
        if (i < 0 || curSize <= i) throw IndexOutOfBoundsException()
        return array[i]
    }

    fun add(elem: Int) {
        if (curSize == maxSize) throw ArrayIndexOutOfBoundsException()
        array[curSize] = elem
        curSize++
    }

    fun removeLast(): Int {
        if (curSize == 0) throw NoSuchElementException()
        curSize--
        return array[curSize]
    }

    fun clear() {
        curSize = 0
    }

    fun copyInto(other: FixedSizeArrayList) {
        if (other.maxSize < this.maxSize) throw IllegalArgumentException()
        other.curSize = this.curSize
        this.array.copyInto(other.array, endIndex = curSize)
    }

    fun sort() {
        array.sort(0, curSize)
    }

    fun sortDescending() {
        array.sortDescending(0, curSize)
    }

    override fun iterator(): Iterator<Int> = object : Iterator<Int> {

        var nextIndex = 0

        override fun hasNext(): Boolean = nextIndex < curSize

        override fun next(): Int {
            nextIndex++
            return array[nextIndex - 1]
        }
    }
}
