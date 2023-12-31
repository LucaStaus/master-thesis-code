package datastructures

import kotlin.math.max

class SetTrie {

    private inner class Node(val label: Int) {

        val map = HashMap<Int, Node>()

        var isEnd = false
        var value: Int = -1

        var maxValue = -1

        fun hasChildLabeled(label: Int): Boolean {
            return map.containsKey(label)
        }

        fun createChild(label: Int): Node {
            if (!hasChildLabeled(label)) {
                map[label] = Node(label)
                numberOfNodes++
            }
            return get(label)
        }

        operator fun get(label: Int): Node {
            return map.getValue(label)
        }
    }

    var wordsStored: Int = 0
        private set
    var numberOfNodes: Int = 1
        private set

    private val root = Node(-1)

    /**
     * Inserts a set represented by the array [word] into this SetTrie and associates that set with [value].
     * If the same set has already been inserted then the value will be overwritten.
     * @param word the array containing the set that should be inserted.
     * @param presorted should only be true if [word] is already sorted in ascending order, false by default.
     */
    fun insert(word: FixedSizeArrayList, value: Int, presorted: Boolean = false) {
        if (!presorted) word.sort()
        var i = 0
        var curNode = root
        var curLabel: Int
        while (i < word.size) {
            curNode.maxValue = max(curNode.maxValue, value)
            curLabel = word[i]
            curNode = if (curNode.hasChildLabeled(curLabel)) {
                curNode[curLabel]
            } else {
                curNode.createChild(curLabel)
            }
            i++
        }
        if (!curNode.isEnd) {
            curNode.isEnd = true
            wordsStored++
        }
        curNode.value = value
        curNode.maxValue = max(curNode.maxValue, value)
    }

    /**
     * Checks if a set represented by the array [word] exists in this SetTrie.
     * @param word the array containing the set that should be checked.
     * @param presorted should only be true if [word] is already sorted in ascending order, false by default.
     * @return the value associated with the given range of [word] if it exists in this SetTrie, null otherwise.
     */
    fun exists(word: FixedSizeArrayList, presorted: Boolean = false): Int? {
        if (!presorted) word.sort()
        var i = 0
        var curNode = root
        var curLabel: Int
        while (i < word.size) {
            curLabel = word[i]
            if (curNode.hasChildLabeled(curLabel)) {
                curNode = curNode[curLabel]
            } else {
                return null
            }
            i++
        }
        return if (curNode.isEnd) curNode.value else null
    }

    /**
     * Checks if a subset of a set represented by the array [word] exists in this SetTrie.
     * @param word the array containing the set that should be checked.
     * @param presorted should only be true if [word] is already sorted in ascending order, false by default.
     * @return the value associated with the first subset of the given range of [word] that can be found if one exists
     * in this SetTrie, null otherwise.
     */
    fun existsSubset(word: FixedSizeArrayList, presorted: Boolean = false): Int? {
        if (!presorted) word.sort()
        return existsSubsetValueRecurse(word, -1, root, 0)
    }

    /**
     * Checks if a subset of a set represented by the array [word] exists in this SetTrie that has
     * at least value [minValue].
     * @param word the array containing the set that should be checked.
     * @param minValue the minimum value the subset needs to have.
     * @param presorted should only be true if [word] is already sorted in ascending order, false by default.
     * @return the value associated with the first subset of the given range of [word] that can be found if one exists
     * in this SetTrie, null otherwise.
     */
    fun existsSubsetValue(word: FixedSizeArrayList, minValue: Int, presorted: Boolean = false): Int? {
        if (!presorted) word.sort()
        return existsSubsetValueRecurse(word, minValue, root, 0)
    }

    private fun existsSubsetValueRecurse(
        word: FixedSizeArrayList,
        minValue: Int,
        curNode: Node,
        curIndex: Int,
    ): Int? {
        if (curNode.isEnd && curNode.value >= minValue) return curNode.value
        if (curIndex == word.size) return null
        if (curNode.maxValue < minValue) return null
        val curLabel = word[curIndex]
        if (curNode.hasChildLabeled(curLabel)) {
            val recurse = existsSubsetValueRecurse(word, minValue, curNode[curLabel], curIndex + 1)
            if (recurse != null) return recurse
        }
        return existsSubsetValueRecurse(word, minValue, curNode, curIndex + 1)
    }

    private var curBestValue = 0

    /**
     * Checks if a subset of a set represented by the array [word] exists in this SetTrie that has
     * at least value [maxValue].
     * @param word the array containing the set that should be checked.
     * @param maxValue the minimum value the subset needs to have.
     * @param presorted should only be true if [word] is already sorted in ascending order, false by default.
     * @return the value associated with the first subset of the given range of [word] that can be found if one exists
     * in this SetTrie, null otherwise.
     */
    fun existsSubsetBestValue(word: FixedSizeArrayList, maxValue: Int, presorted: Boolean = false): Int {
        if (!presorted) word.sort()
        curBestValue = 0
        existsSubsetBestValueRecurse(word, maxValue, root, 0)
        return curBestValue
    }

    private fun existsSubsetBestValueRecurse(
        word: FixedSizeArrayList,
        maxValue: Int,
        curNode: Node,
        curIndex: Int,
    ): Int? {
        if (curNode.isEnd && curNode.value > curBestValue) curBestValue = curNode.value
        if (curBestValue >= maxValue) return curBestValue
        if (curIndex == word.size) return null
        if (curNode.maxValue <= curBestValue) return null
        val curLabel = word[curIndex]
        if (curNode.hasChildLabeled(curLabel)) {
            val recurse = existsSubsetBestValueRecurse(word, maxValue, curNode[curLabel], curIndex + 1)
            if (recurse != null) return recurse
        }
        return existsSubsetBestValueRecurse(word, maxValue, curNode, curIndex + 1)
    }
}
