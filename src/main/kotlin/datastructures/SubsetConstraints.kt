package datastructures

class SubsetConstraints(
    exampleCount: Int,
    vertexCount: Int,
) {
    val isAConstraintBroken: Boolean
        get() = brokenCount > 0

    private val isInConstraint = Array(vertexCount) { BooleanArray(exampleCount) }
    private val curSize = IntArray(vertexCount) { -1 }
    private val head = IntArray(vertexCount) { -1 }
    private val next = Array(vertexCount) { IntArray(exampleCount) }
    private var brokenCount = 0

    /**
     * Creates a new empty subset constraint for [v]. If a subset constraint already exists nothing happens.
     */
    fun createConstraint(v: Int) {
        if (curSize[v] != -1) return
        curSize[v] = 0
        brokenCount++
    }

    /**
     * Removes the current subset constraint for [v]. If no subset constraint exists nothing happens.
     */
    fun removeConstraint(v: Int) {
        if (curSize[v] == -1) return
        var cur = head[v]
        while (cur != -1) {
            isInConstraint[v][cur] = false
            cur = next[v][cur]
        }
        if (curSize[v] == 0) {
            // in this case the constraint was broken and is now being reset before the examples are
            // being added to the subtree again
            brokenCount--
        }
        head[v] = -1
        curSize[v] = -1
    }

    /**
     * Adds [e] to the subset constraint of [v]. If no subset constraint exists one will be created.
     * If [e] is already part of the subset constraint nothing happens.
     * This assumes that [e] is currently in the subtree of [v].
     */
    fun addExample(v: Int, e: Int) {
        if (isInConstraint[v][e]) return
        createConstraint(v)
        isInConstraint[v][e] = true
        val head = head[v]
        next[v][e] = head
        this.head[v] = e
        updateAdd(v, e)
    }

    /**
     * This should be called if [e] is removed from the subtree of [v].
     */
    fun updateRemove(v: Int, e: Int) {
        if (isInConstraint[v][e]) {
            curSize[v]--
            if (curSize[v] == 0) {
                brokenCount++
            }
        }
    }

    /**
     * This should be called if [e] is added to the subtree of [v].
     */
    fun updateAdd(v: Int, e: Int) {
        if (isInConstraint[v][e]) {
            curSize[v]++
            if (curSize[v] == 1) {
                brokenCount--
            }
        }
    }
}
