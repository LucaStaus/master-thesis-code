package datastructures

class MultiLinkedList(
    maxLists: Int,
    maxCombinedSize: Int,
    initialList: Int,
) {

    private val prev = IntArray(maxCombinedSize) { it - 1 }
    private val next = IntArray(maxCombinedSize) { it + 1 }
    private val heads = IntArray(maxLists) { -1 }
    private val tails = IntArray(maxLists) { -1 }

    private val listAssignment = IntArray(maxCombinedSize) { initialList }
    private val listSize = IntArray(maxLists) { if (it == initialList) maxCombinedSize else 0 }

    init {
        if (maxCombinedSize > 0) {
            next[maxCombinedSize - 1] = -1
            heads[initialList] = 0
            tails[initialList] = maxCombinedSize - 1
        }
    }

    inner class MLLIterator(list: Int) : Iterator<Int> {

        private var curElem = -1
        private var nextElem = getHead(list)

        override fun hasNext(): Boolean = nextElem != -1

        override fun next(): Int {
            if (!hasNext()) throw NoSuchElementException()
            curElem = nextElem
            nextElem = getNext(curElem)
            return curElem
        }

        /**
         * Moves the element that was returned by the last call to [next] to a new list indicated by [newList].
         * @throws NoSuchElementException if [next] has not been called or this function has already been called
         * after the last call to [next].
         */
        fun moveCurElem(newList: Int) {
            if (curElem == -1) throw NoSuchElementException()
            move(curElem, newList)
            curElem = -1
        }
    }

    /**
     * Returns an Iterator over all elements in [list]
     * that can move the current element of the iteration to a new list.
     */
    fun getIterator(list: Int) = MLLIterator(list)

    /**
     * Returns the first element of [list].
     * Returns -1 if [list] is empty.
     */
    fun getHead(list: Int) = heads[list]

    /**
     * Returns the last element of [list].
     * Returns -1 if [list] is empty.
     */
    fun getTail(list: Int) = tails[list]

    /**
     * Returns the element that comes after [elem] in its list.
     * Returns -1 if [elem] is the last element in its list.
     */
    fun getNext(elem: Int) = next[elem]

    /**
     * Returns the element that comes before [elem] in its list.
     * Returns -1 if [elem] is the first element in its list.
     */
    fun getPrev(elem: Int) = prev[elem]

    /**
     * Returns the number of elements in [list].
     */
    fun getSize(list: Int) = listSize[list]

    /**
     * Returns the list of [elem].
     */
    fun getListOf(elem: Int) = listAssignment[elem]

    /**
     * Moves [elem] from its current list to the end of [newList].
     */
    fun move(elem: Int, newList: Int) {
        // remove elem from curList
        if (prev[elem] != -1) {
            next[prev[elem]] = next[elem]
        } else {
            heads[listAssignment[elem]] = next[elem]
        }
        if (next[elem] != -1) {
            prev[next[elem]] = prev[elem]
        } else {
            tails[listAssignment[elem]] = prev[elem]
        }
        listSize[listAssignment[elem]]--
        // add elem to newList
        listAssignment[elem] = newList
        listSize[newList]++
        if (tails[newList] == -1) {
            heads[newList] = elem
            prev[elem] = -1
        } else {
            next[tails[newList]] = elem
            prev[elem] = tails[newList]
        }
        tails[newList] = elem
        next[elem] = -1
    }

    /**
     * Moves all elements in [curList] to the end of [newList].
     */
    fun moveList(curList: Int, newList: Int) {
        if (listSize[curList] == 0) return
        // update listAssignment before making any other changes
        forEach(curList) { e ->
            listAssignment[e] = newList
        }
        if (listSize[newList] == 0) {
            // here we know that only newList is empty
            heads[newList] = heads[curList]
            tails[newList] = tails[curList]
            listSize[newList] = listSize[curList]
        } else {
            // here we know that both lists are not empty
            next[tails[newList]] = heads[curList]
            prev[heads[curList]] = tails[newList]
            tails[newList] = tails[curList]
            listSize[newList] += listSize[curList]
        }
        heads[curList] = -1
        tails[curList] = -1
        listSize[curList] = 0
    }

    /**
     * Calls [action] for every element in [list].
     * [action] should not call [move].
     */
    inline fun forEach(list: Int, action: (Int) -> Unit) {
        var nextElem = getHead(list)
        while (nextElem != -1) {
            action(nextElem)
            nextElem = getNext(nextElem)
        }
    }
}
