import datastructures.*
import lowerbounds.ImpLB
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class WitnessTree(
    val data: NormalizedDataSet,
    private val maxSize: Int,
    private val algoType: AlgoType,
    rootWitness: Int,
) {

    // ----------------
    // important values
    // ----------------
    val maxLeafCount = maxSize + 1
    val maxInnerCount = maxSize
    val maxVertexCount = maxLeafCount + maxInnerCount

    // ----------------------------------------
    // variables for the general tree structure
    // ----------------------------------------

    // initially the tree is just a single leaf
    var root = maxSize
        private set
    val parent = IntArray(maxVertexCount)
    val leftChild = IntArray(maxInnerCount)
    val rightChild = IntArray(maxInnerCount)
    private var nextInner = 0
    private var nextLeaf = maxSize + 1
    val curInnerCount
        get() = nextInner
    val curLeafCount
        get() = nextLeaf - maxInnerCount
    val lastAddedInner
        get() = nextInner - 1
    val lastAddedLeaf
        get() = nextLeaf - 1

    // ---------------------------------------------------
    // variables for saving certain values for each vertex
    // ---------------------------------------------------
    val dim = IntArray(maxVertexCount) // inner vertices only
    val thr = IntArray(maxVertexCount) // inner vertices only
    val cla = BooleanArray(maxVertexCount) // leafs only
    val wit = IntArray(maxVertexCount) // leafs only

    // depth[v] is the number of edges on the path from v to root
    val depth = IntArray(maxVertexCount)

    // maxDepth[v] is the maximum depth of any vertex in the subtree with root v
    val maxDepth = IntArray(maxVertexCount)

    // size[v] is the number of inner vertices in the subtree with root v
    val size = IntArray(maxVertexCount)

    // isCorrectlyClassified[v] is true if all examples in the subtree with root v are correctly classified
    val isCorrectlyClassified = BooleanArray(maxVertexCount)

    // hasBeenModified[v] is true if the last tree modification either removed or added at least one example to the
    // subtree of v
    val hasBeenModified = BooleanArray(maxVertexCount)

    // ---------------------------------------------------
    // variables for keeping track of examples in the tree
    // ---------------------------------------------------

    val notDirtyList = IntArray(maxVertexCount) { v -> if (isLeaf(v)) v - maxSize else -1 }
    val dirtyList = IntArray(maxVertexCount) { v -> if (isLeaf(v)) v + 1 else -1 }
    val extraList1 = 2 * maxLeafCount
    val extraList2 = 2 * maxLeafCount + 1
    val removedList = 2 * maxLeafCount + 2
    val eLists = MultiLinkedList(maxLeafCount * 2 + 3, data.n, extraList1)

    // list for dynamically remembering the path through a subtree
    private val curTreePath = FixedSizeArrayList(maxVertexCount)

    // values used for sorting dirty examples
    private val dirtySort = IntArray(data.n)
    private val calcDirtySortValue = if (algoType.useDirtyPriority) {
        this::calcDirtySortValueLegalCuts
    } else {
        this::calcDirtySortValueDefault
    }

    // used to store the examples for which the dirty value needs to be recalculated
    private val updateDirtySort = FixedSizeArrayList(data.n)
    private val dirtyComparator = Comparator.comparingInt<Int> { e -> dirtySort[e] }

    // a priority queue for each leaf that sorts dirty examples by dirtySort in ascending order
    private val dirtyExamples = Array(maxVertexCount) {
        PriorityQueue(data.n, dirtyComparator)
    }

    // -------------------
    // threshold variables
    // -------------------
    val leftWitThr = Array(data.d) { IntArray(maxVertexCount) }
    val rightWitThr = Array(data.d) { IntArray(maxVertexCount) }
    val leftDirtyThrTrue = Array(data.d) { IntArray(maxVertexCount) }
    val rightDirtyThrTrue = Array(data.d) { IntArray(maxVertexCount) }
    val leftDirtyThrFalse = Array(data.d) { IntArray(maxVertexCount) }
    val rightDirtyThrFalse = Array(data.d) { IntArray(maxVertexCount) }

    // ---------------------
    // lower bound variables
    // ---------------------
    val impLB = ImpLB(this)
    // val pairLB = PairLB1(env, data)

    // --------------------------------
    // variables for subset constraints
    // --------------------------------
    val isASubsetConstraintBroken
        get() = thresholdSubConst.isAConstraintBroken || heightSubConst.isAConstraintBroken
    private val thresholdSubConst = SubsetConstraints(data.n, maxVertexCount)
    private val heightSubConst = SubsetConstraints(data.n, maxVertexCount)

    init {
        cla[root] = data.cla[rootWitness]
        wit[root] = rootWitness
        depth[root] = 0
        size[root] = 0
        moveExamplesFromList(extraList1, root)
        postTreeChangeOperations()
    }

    // ----------------------------------------------------------------------------------------------------------------
    //                                       General Helper Functions
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * Returns True iff [vertex] is a leaf.
     */
    fun isLeaf(vertex: Int) = vertex >= maxSize

    /**
     * Returns true if the example [e] would be dirty in [leaf].
     */
    fun isDirtyInLeaf(e: Int, leaf: Int) = cla[leaf] != data.cla[e]

    /**
     * Returns the leaf that the example [e] is assigned to.
     */
    fun getLeafOf(e: Int) = eLists.getListOf(e).let { if (it <= maxSize) it + maxSize else it - 1 }

    /**
     * Returns the number of examples currently assigned to [leaf].
     */
    fun getLeafExampleCount(leaf: Int): Int {
        return eLists.getSize(dirtyList[leaf]) + eLists.getSize(notDirtyList[leaf])
    }

    // ----------------------------------------------------------------------------------------------------------------
    //                                     Tree Structure Change Functions
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * Performs a one-step refinement of the current tree. The function assumes that the one-step refinement does not
     * change the leaf of any witness in the tree.
     *
     * Whether the new leaf will be the left or the right child of the new inner vertex is decided based on what the
     * value of [newWit] in dimension [dim] is in relation to the threshold. The class of the new leaf will be the
     * class of [newWit].
     *
     * @param vertex if vertex is the root the one-step refinement will add a new root. Otherwise, the edge from
     * [vertex] to its parent is bisected.
     * @param dim the dimension of the new inner vertex.
     * @param thr the threshold of the new inner vertex.
     * @param newWit the vertex that will become the witness of the new leaf.
     */
    fun refineTree(vertex: Int, dim: Int, thr: Int, newWit: Int) {
        preTreeChangeOperations()
        // left is true if the new leaf will be the left child of the new inner vertex
        val left = data.values[newWit][dim] <= thr

        // perform one-step refinement
        // start by performing the necessary structural changes of the tree
        if (vertex == root) {
            root = nextInner
            depth[nextInner] = 0
            depth[nextLeaf] = 1
        } else {
            val p = parent[vertex]
            // make sure nextInner is on the same side as vertex was previously
            if (leftChild[p] == vertex) {
                leftChild[p] = nextInner
            } else {
                rightChild[p] = nextInner
            }
            parent[nextInner] = p
            depth[nextInner] = depth[p] + 1
            depth[nextLeaf] = depth[nextInner] + 1
        }
        parent[vertex] = nextInner
        parent[nextLeaf] = nextInner
        if (left) {
            leftChild[nextInner] = nextLeaf
            rightChild[nextInner] = vertex
        } else {
            leftChild[nextInner] = vertex
            rightChild[nextInner] = nextLeaf
        }
        this.dim[nextInner] = dim
        this.thr[nextInner] = thr
        this.cla[nextLeaf] = data.cla[newWit]
        this.wit[nextLeaf] = newWit

        // add height subset constraint
        if (algoType.useSubsetConstraints && !isLeaf(vertex)) {
            val otherChild = if (data.values[newWit][this.dim[vertex]] <= this.thr[vertex]) {
                rightChild[vertex]
            } else {
                leftChild[vertex]
            }
            createHeightSubsetConstraint(lastAddedInner, otherChild)
        }

        // update assignments of all examples in the subtree of vertex
        if (left) {
            moveExamplesToList(vertex, extraList1) { curE ->
                data.values[curE][dim] <= thr
            }
        } else {
            moveExamplesToList(vertex, extraList1) { curE ->
                data.values[curE][dim] > thr
            }
        }
        moveExamplesFromList(extraList1, nextLeaf)

        nextInner++
        nextLeaf++
        postTreeChangeOperations()
    }

    /**
     * Updates the last one-step refinement that has been performed on this tree by changing the dimension and
     * threshold of the inner vertex that was added in that refinement to [dim] and [thr] respectively.
     * The function assumes that such a refinement exists and that the new one-step refinement does not change
     * the leaf of any witness in the tree.
     * @param dim the new dimension of the inner vertex from the last refinement.
     * @param thr the new threshold of the inner vertex from the last refinement.
     */
    fun updateLastRefinement(dim: Int, thr: Int) {
        preTreeChangeOperations()
        // make sure subset constraints are removed if they exist
        if (algoType.useSubsetConstraints) thresholdSubConst.removeConstraint(lastAddedInner)
        val curWit = wit[lastAddedLeaf]
        // left is true if lastAddedLeaf is the left child of lastAddedInner
        var left = leftChild[lastAddedInner] == lastAddedLeaf
        // vertex is the sibling of lastAddedLeaf
        val vertex = if (left) rightChild[lastAddedInner] else leftChild[lastAddedInner]
        // switch sides is true if the new dimension and threshold cause the sides of lastAddedLeaf and vertex
        // to be switched
        val switchSides = (data.values[curWit][dim] <= thr) != left

        // perform one-step refinement
        // start by performing the necessary structural changes of the tree
        // switch the left and right child of lastAddedInner if switchSides is true
        if (switchSides) {
            val l = leftChild[lastAddedInner]
            leftChild[lastAddedInner] = rightChild[lastAddedInner]
            rightChild[lastAddedInner] = l
            // make sure left has the correct value after switching sides
            left = !left
        }
        // update dimension and threshold of lastAddedInner
        this.dim[lastAddedInner] = dim
        this.thr[lastAddedInner] = thr

        if (left) {
            // update assignments of all examples in the subtree of vertex
            moveExamplesToList(vertex, extraList1) { curE ->
                data.values[curE][dim] <= thr
            }
            // update assignments of all examples currently assigned to nextLeaf that no longer belong there
            moveExamplesToList(lastAddedLeaf, extraList2) { curE ->
                data.values[curE][dim] > thr
            }
        } else {
            // update assignments of all examples in the subtree of vertex
            moveExamplesToList(vertex, extraList1) { curE ->
                data.values[curE][dim] > thr
            }
            // update assignments of all examples currently assigned to nextLeaf that no longer belong there
            moveExamplesToList(lastAddedLeaf, extraList2) { curE ->
                data.values[curE][dim] <= thr
            }
        }
        moveExamplesFromList(extraList1, lastAddedLeaf)
        moveExamplesFromList(extraList2, vertex)
        postTreeChangeOperations()
    }

    /**
     * Updates the last one-step refinement that has been performed on this tree by changing the
     * threshold of the inner vertex that was added in that refinement to [thr].
     * The function assumes that such a refinement exists and that the new one-step refinement does not change
     * the leaf of any witness in the tree.
     * @param thr the new threshold of the inner vertex from the last refinement.
     */
    fun updateLastRefinement(thr: Int) {
        preTreeChangeOperations()
        // make sure subset constraints are removed if they exist
        if (algoType.useSubsetConstraints) thresholdSubConst.removeConstraint(lastAddedInner)
        val oldThr = this.thr[lastAddedInner]
        val curDim = this.dim[lastAddedInner]
        val curWit = this.wit[lastAddedLeaf]
        val leafWitValue = data.values[curWit][curDim]
        // left is true if lastAddedLeaf is the left child of lastAddedInner
        val left = leftChild[lastAddedInner] == lastAddedLeaf
        // vertex is the sibling of lastAddedLeaf
        val vertex = if (left) rightChild[lastAddedInner] else leftChild[lastAddedInner]

        // perform one-step refinement
        // start by performing the necessary structural changes of the tree
        this.thr[lastAddedInner] = thr

        // move examples
        if (leafWitValue <= oldThr) {
            if (thr < oldThr) {
                moveExamplesToList(lastAddedLeaf, extraList1) { curE ->
                    data.values[curE][curDim] > thr
                }
                moveExamplesFromList(extraList1, vertex)
            } else {
                moveExamplesToList(vertex, extraList1) { curE ->
                    data.values[curE][curDim] <= thr
                }
                // add examples that were newly added to lastAddedLeaf as a new threshold subset constraint
                if (algoType.useSubsetConstraints) createThresholdSubsetConstraint(lastAddedInner, extraList1)
                moveExamplesFromList(extraList1, lastAddedLeaf)
            }
        } else {
            if (thr < oldThr) {
                moveExamplesToList(vertex, extraList1) { curE ->
                    data.values[curE][curDim] > thr
                }
                // add examples that were newly added to lastAddedLeaf as a new threshold subset constraint
                if (algoType.useSubsetConstraints) createThresholdSubsetConstraint(lastAddedInner, extraList1)
                moveExamplesFromList(extraList1, lastAddedLeaf)
            } else {
                moveExamplesToList(lastAddedLeaf, extraList1) { curE ->
                    data.values[curE][curDim] <= thr
                }
                moveExamplesFromList(extraList1, vertex)
            }
        }
        postTreeChangeOperations()
    }

    /**
     * Reverts the last one-step refinement that has been performed on this tree.
     * The function assumes that such a refinement exists.
     */
    fun revertLastRefinement() {
        preTreeChangeOperations()
        nextInner--
        nextLeaf--
        // make sure subset constraints are removed if they exist
        if (algoType.useSubsetConstraints) {
            thresholdSubConst.removeConstraint(nextInner)
            heightSubConst.removeConstraint(nextInner)
        }
        // left is true if lastAddedLeaf is the left child of lastAddedInner
        val left = leftChild[nextInner] == nextLeaf
        // vertex is the sibling of lastAddedLeaf
        val vertex = if (left) rightChild[nextInner] else leftChild[nextInner]

        // revert the last one-step refinement
        // start by performing the necessary structural changes of the tree
        if (nextInner == root) {
            root = vertex
        } else {
            val p = parent[nextInner]
            // make sure vertex is on the same side as nextInner was previously
            if (leftChild[p] == nextInner) {
                leftChild[p] = vertex
            } else {
                rightChild[p] = vertex
            }
            parent[vertex] = p
        }
        // update assignments of all examples currently assigned to nextLeaf
        moveExamplesToList(nextLeaf, extraList1) { true }
        moveExamplesFromList(extraList1, vertex)

        postTreeChangeOperations()
    }

    fun reduceExamplesToSubset(subset: FixedSizeArrayList) {
        // this is only allowed if the tree is just a single leaf
        if (size[root] != 0) throw IllegalStateException()
        preTreeChangeOperations()
        moveExamplesToList(root, removedList) { true }
        for (e in subset) {
            eLists.move(e, extraList1)
        }
        moveExamplesFromList(extraList1, root)
        postTreeChangeOperations()
    }

    private fun preTreeChangeOperations() {
        updateDirtySort.clear()
        for (v in hasBeenModified.indices) {
            hasBeenModified[v] = false
        }
    }

    private fun postTreeChangeOperations() {
        updateValuesInSubtree(root)
        updateDirtySort.forEach { e ->
            val l = getLeafOf(e)
            dirtySort[e] = calcDirtySortValue(e, l)
            dirtyExamples[l].add(e)
        }
    }

    private fun updateValuesInSubtree(subRoot: Int) {
        if (subRoot == root) {
            depth[subRoot] = 0
        } else {
            depth[subRoot] = depth[parent[subRoot]] + 1
        }
        if (isLeaf(subRoot)) {
            size[subRoot] = 0
            isCorrectlyClassified[subRoot] = eLists.getSize(dirtyList[subRoot]) == 0
            maxDepth[subRoot] = depth[subRoot]
        } else {
            val left = leftChild[subRoot]
            val right = rightChild[subRoot]
            updateValuesInSubtree(left)
            updateValuesInSubtree(right)
            size[subRoot] = size[left] + size[right] + 1
            isCorrectlyClassified[subRoot] = isCorrectlyClassified[left] && isCorrectlyClassified[right]
            maxDepth[subRoot] = max(maxDepth[left], maxDepth[right])
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    //                                           Example Move Functions
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * Returns the dirty example with the lowest sort value within the subtree given by [subRoot].
     */
    fun getLowestDirtyExample(subRoot: Int): Int {
        if (isCorrectlyClassified[subRoot]) return -1
        return if (isLeaf(subRoot)) {
            dirtyExamples[subRoot].peek()
        } else {
            val leftMin = getLowestDirtyExample(leftChild[subRoot])
            val rightMin = getLowestDirtyExample(rightChild[subRoot])
            if (leftMin == -1) {
                rightMin
            } else if (rightMin == -1) {
                leftMin
            } else if (dirtySort[leftMin] <= dirtySort[rightMin]) {
                leftMin
            } else {
                rightMin
            }
        }
    }

    /**
     * Moves all examples in [subRoot] that match [condition] to [newList].
     * Also calls [removeExampleFromSubtree] for all appropriate examples and vertices.
     */
    private fun moveExamplesToList(subRoot: Int, newList: Int, condition: (Int) -> Boolean) {
        curTreePath.add(subRoot)
        if (!isLeaf(subRoot)) {
            moveExamplesToList(leftChild[subRoot], newList, condition)
            moveExamplesToList(rightChild[subRoot], newList, condition)
        } else {
            val iterNotDirty = eLists.getIterator(notDirtyList[subRoot])
            for (curE in iterNotDirty) {
                if (condition(curE)) {
                    iterNotDirty.moveCurElem(newList)
                    for (v in curTreePath) {
                        removeExampleFromSubtree(curE, v)
                    }
                }
            }
            val iterDirty = eLists.getIterator(dirtyList[subRoot])
            for (curE in iterDirty) {
                if (condition(curE)) {
                    iterDirty.moveCurElem(newList)
                    for (v in curTreePath) {
                        removeExampleFromSubtree(curE, v)
                    }
                }
            }
        }
        curTreePath.removeLast()
    }

    /**
     * Moves all examples in [curList] to the correct leaf in the subtree with root [newSubRoot].
     * Also calls [addExampleToSubtree] for all appropriate examples and vertices.
     */
    private fun moveExamplesFromList(curList: Int, newSubRoot: Int) {
        val iter = eLists.getIterator(curList)
        for (curE in iter) {
            val newLeaf = getLeafOfExampleInSubtree(curE, newSubRoot)
            if (data.cla[curE] != cla[newLeaf]) {
                iter.moveCurElem(dirtyList[newLeaf])
            } else {
                iter.moveCurElem(notDirtyList[newLeaf])
            }
            for (v in curTreePath) {
                addExampleToSubtree(curE, v)
            }
            curTreePath.clear()
        }
    }

    /**
     * Finds the correct leaf for the example [e] in the subtree with root [subRoot].
     * Also adds all vertices on the path from [subRoot] to the new leaf to [curTreePath].
     */
    private fun getLeafOfExampleInSubtree(e: Int, subRoot: Int): Int {
        var curRoot = subRoot
        while (!isLeaf(curRoot)) {
            curTreePath.add(curRoot)
            curRoot = if (data.values[e][dim[curRoot]] <= thr[curRoot]) {
                leftChild[curRoot]
            } else {
                rightChild[curRoot]
            }
        }
        curTreePath.add(curRoot)
        return curRoot
    }

    /**
     * Function that is called when the example [e] has been removed from the subtree with root [subRoot].
     * Should only be called after [e] has already been removed from the correct list in [eLists].
     */
    private fun removeExampleFromSubtree(e: Int, subRoot: Int) {
        hasBeenModified[subRoot] = true
        if (isLeaf(subRoot)) {
            if (isDirtyInLeaf(e, subRoot)) {
                dirtyExamples[subRoot].remove(e)
            }
        } else if (algoType.useSubsetConstraints) {
            thresholdSubConst.updateRemove(subRoot, e)
            heightSubConst.updateRemove(subRoot, e)
        }
    }

    /**
     * Function that is called when the example [e] has been added to the subtree with root [subRoot].
     * Should only be called after [e] has already been added to the correct list in [eLists].
     */
    private fun addExampleToSubtree(e: Int, subRoot: Int) {
        hasBeenModified[subRoot] = true
        if (isLeaf(subRoot)) {
            if (isDirtyInLeaf(e, subRoot)) {
                updateDirtySort.add(e)
            }
        } else if (algoType.useSubsetConstraints) {
            thresholdSubConst.updateAdd(subRoot, e)
            heightSubConst.updateAdd(subRoot, e)
        }
    }

    private fun calcDirtySortValueCutsTotal(e: Int, l: Int): Int {
        val wit = wit[l]
        var sum = 0
        for (i in 0..<data.d) {
            sum += abs(data.values[e][i] - data.values[wit][i])
        }
        return sum * depth[l]
    }

    private fun calcDirtySortValueDefault(e: Int, l: Int): Int {
        return e
    }

    private fun calcDirtySortValueLegalCuts(e: Int, l: Int): Int {
        var sum = 0
        for (dim in 0..<data.d) {
            var curV = l
            while (curV != -1) {
                sum += if (data.values[e][dim] < leftWitThr[dim][curV]) {
                    leftWitThr[dim][curV] - data.values[e][dim]
                } else if (data.values[e][dim] > rightWitThr[dim][curV]) {
                    data.values[e][dim] - rightWitThr[dim][curV]
                } else {
                    break
                }
                curV = if (curV != root) parent[curV] else -1
            }
        }
        return sum
    }

    // ----------------------------------------------------------------------------------------------------------------
    //                                          Threshold Update Functions
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * Updates the witness thresholds for every dimension and every vertex in the subtree of [subRoot].
     */
    fun updateWitnessThresholds(subRoot: Int) {
        if (!isLeaf(subRoot)) {
            val leftChild = leftChild[subRoot]
            val rightChild = rightChild[subRoot]
            updateWitnessThresholds(leftChild)
            updateWitnessThresholds(rightChild)
            for (dim in 0..<data.d) {
                leftWitThr[dim][subRoot] = min(leftWitThr[dim][leftChild], leftWitThr[dim][rightChild])
                rightWitThr[dim][subRoot] = max(rightWitThr[dim][leftChild], rightWitThr[dim][rightChild])
            }
        } else {
            for (dim in 0..<data.d) {
                leftWitThr[dim][subRoot] = data.values[wit[subRoot]][dim]
                rightWitThr[dim][subRoot] = data.values[wit[subRoot]][dim]
            }
        }
    }

    /**
     * Updates the dirty thresholds for every dimension and every vertex in the subtree of [subRoot].
     */
    fun updateDirtyThresholds(subRoot: Int) {
        if (!isLeaf(subRoot)) {
            val leftChild = leftChild[subRoot]
            val rightChild = rightChild[subRoot]
            updateDirtyThresholds(leftChild)
            updateDirtyThresholds(rightChild)
            for (dim in 0..<data.d) {
                leftDirtyThrTrue[dim][subRoot] =
                    min(leftDirtyThrTrue[dim][leftChild], leftDirtyThrTrue[dim][rightChild])
                rightDirtyThrTrue[dim][subRoot] =
                    max(rightDirtyThrTrue[dim][leftChild], rightDirtyThrTrue[dim][rightChild])
                leftDirtyThrFalse[dim][subRoot] =
                    min(leftDirtyThrFalse[dim][leftChild], leftDirtyThrFalse[dim][rightChild])
                rightDirtyThrFalse[dim][subRoot] =
                    max(rightDirtyThrFalse[dim][leftChild], rightDirtyThrFalse[dim][rightChild])
            }
        } else {
            for (dim in 0..<data.d) {
                var leftTrue = Int.MAX_VALUE
                var rightTrue = -1
                var leftFalse = Int.MAX_VALUE
                var rightFalse = -1
                eLists.forEach(dirtyList[subRoot]) { curE ->
                    if (data.cla[curE] && leftTrue > data.values[curE][dim]) {
                        leftTrue = data.values[curE][dim]
                    }
                    if (data.cla[curE] && rightTrue < data.values[curE][dim]) {
                        rightTrue = data.values[curE][dim]
                    }
                    if (!data.cla[curE] && leftFalse > data.values[curE][dim]) {
                        leftFalse = data.values[curE][dim]
                    }
                    if (!data.cla[curE] && rightFalse < data.values[curE][dim]) {
                        rightFalse = data.values[curE][dim]
                    }
                }
                leftDirtyThrTrue[dim][subRoot] = leftTrue
                rightDirtyThrTrue[dim][subRoot] = rightTrue
                leftDirtyThrFalse[dim][subRoot] = leftFalse
                rightDirtyThrFalse[dim][subRoot] = rightFalse
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    //                                          Subset Constraint Functions
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a threshold subset constraint for the vertex [v] and adds all examples in [list].
     */
    private fun createThresholdSubsetConstraint(v: Int, list: Int) {
        thresholdSubConst.createConstraint(v)
        eLists.forEach(list) { curE ->
            thresholdSubConst.addExample(v, curE)
        }
    }

    /**
     * Creates a height subset constraint for the vertex [v] and adds all dirty examples in the subtree with
     * root [subRoot].
     */
    private fun createHeightSubsetConstraint(v: Int, subRoot: Int) {
        heightSubConst.createConstraint(v)
        addExamplesToHeightSubsetConstraint(v, subRoot)
    }

    /**
     * Adds all dirty examples in the subtree with root [subRoot] to the height subset constraint of vertex [v].
     */
    private fun addExamplesToHeightSubsetConstraint(v: Int, subRoot: Int) {
        if (isLeaf(subRoot)) {
            eLists.forEach(dirtyList[subRoot]) { e ->
                heightSubConst.addExample(v, e)
            }
        } else {
            addExamplesToHeightSubsetConstraint(v, leftChild[subRoot])
            addExamplesToHeightSubsetConstraint(v, rightChild[subRoot])
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    //                                           Decision Tree Functions
    // ----------------------------------------------------------------------------------------------------------------

    /**
     * Returns a [DecisionTree] with the same structure as this witness tree but with its thresholds converted back
     * to the original not normalized thresholds.
     */
    fun getDecisionTree(): DecisionTree {
        val t = DecisionTree(curInnerCount)
        val leafDiff = maxInnerCount - curInnerCount
        t.root = if (isLeaf(root)) root - leafDiff else root
        for (inner in 0..<curInnerCount) {
            t.parent[inner] = parent[inner]
            t.leftChild[inner] = if (isLeaf(leftChild[inner])) leftChild[inner] - leafDiff else leftChild[inner]
            t.rightChild[inner] = if (isLeaf(rightChild[inner])) rightChild[inner] - leafDiff else rightChild[inner]
            t.dim[inner] = data.conversion[dim[inner]][thr[inner]].first
            t.thr[inner] = data.conversion[dim[inner]][thr[inner]].second
        }
        for (leaf in 0..<curLeafCount) {
            val offsetHere = maxInnerCount
            val offsetThere = curInnerCount
            t.parent[leaf + offsetThere] = parent[leaf + offsetHere]
            t.cla[leaf + offsetThere] = cla[leaf + offsetHere]
        }
        return t
    }
}
