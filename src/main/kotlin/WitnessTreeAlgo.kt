import datastructures.DataSet
import datastructures.DecisionTree
import datastructures.FixedSizeArrayList
import datastructures.SetTrie
import gurobi.GRB
import gurobi.GRBEnv
import lowerbounds.PairLBInitial
import kotlin.math.abs

class WitnessTreeAlgo(
    private val data: DataSet,
    private val algoType: AlgoType,
) {

    // --------------
    // info variables
    // --------------

    var searchTreeNodes = 0
        private set
    var lowerBoundEffect = 0
        private set
    var subsetConstraintEffect = 0
        private set
    var uniqueSets = 0
        private set
    var setTrieSize = 0
        private set
    var copiedSets = 0
        private set

    // -------------------
    // important variables
    // -------------------

    var timeoutHappened = false
    private val env = GRBEnv()

    private val normData = if (algoType.doPreProcessing) data.reduceAndNormalize() else data.normalize()
    private lateinit var mainW: WitnessTree
    private lateinit var altW: WitnessTree
    private var firstDirtyExample = 0

    private val setTrieMaxSetSize = 30

    // ------------------
    // set trie variables
    // ------------------

    private val setTrie = SetTrie()
    private val word = FixedSizeArrayList(normData.n)
    private lateinit var leafsToCalculate: FixedSizeArrayList

    init {
        env.set(GRB.IntParam.Threads, 1)
        env.set(GRB.IntParam.OutputFlag, 0)
        env.set(GRB.IntParam.LogToConsole, 0)
        env.start()
    }

    // ----------------------------------------------------------------------------------------------------------------
    //                                             Main Functions
    // ----------------------------------------------------------------------------------------------------------------

    fun findTree(s: Int, upperBound: Int): DecisionTree? {
        resetInfoVariables()
        return when (algoType.strategy) {
            1 -> findTreeStrategy1()
            2 -> findTreeStrategy2(upperBound)
            3 -> findTreeStrategy3(upperBound)
            else -> findTreeWithMaximumSize(s)
        }
    }

    private fun findTreeStrategy1(): DecisionTree? {
        val lb = if (algoType.useLowerBounds) {
            val pairLB = PairLBInitial(env, normData)
            val bound = pairLB.calcLowerBound(0)
            pairLB.dispose()
            bound
        } else {
            1
        }
        var tree: DecisionTree? = null
        var curMaxSize = lb
        while (tree == null && !timeoutHappened) {
            println("Trying max size $curMaxSize")
            tree = findTreeWithMaximumSize(curMaxSize)
            curMaxSize++
        }
        return tree
    }

    private fun findTreeStrategy2(upperBound: Int): DecisionTree? {
        var prevTree: DecisionTree? = null
        var tree: DecisionTree? = null
        var curMaxSize = upperBound
        do {
            println("Trying max size $curMaxSize")
            prevTree = tree
            tree = findTreeWithMaximumSize(curMaxSize)
            curMaxSize--
        } while (tree != null && !timeoutHappened)
        return if (tree == null) prevTree else null
    }

    private fun findTreeStrategy3(upperBound: Int): DecisionTree? {
        // lb is always a size for which the algorithm would return null
        var lb = if (algoType.useLowerBounds) {
            val pairLB = PairLBInitial(env, normData)
            val bound = pairLB.calcLowerBound(0)
            pairLB.dispose()
            bound - 1
        } else {
            0
        }
        // ub is always a size for which the algorithm would return a correct tree
        var ub = upperBound
        println("Trying max size $ub")
        var bestTree = findTreeWithMaximumSize(ub)
        while (ub - lb > 1 && !timeoutHappened) {
            val curMaxSize = lb + ((ub - lb) / 2)
            println("Trying max size $curMaxSize")
            val tree = findTreeWithMaximumSize(curMaxSize)
            if (tree == null) {
                lb = curMaxSize
            } else {
                ub = curMaxSize
                bestTree = tree
            }
        }
        return if (ub - lb > 1 || timeoutHappened) null else bestTree
    }

    private fun findTreeWithMaximumSize(maxSize: Int): DecisionTree? {
        val rootWitness = if (algoType.useDirtyPriority) {
            val (rootWit, firstDirty) = calcStartingPair()
            firstDirtyExample = firstDirty
            rootWit
        } else {
            0
        }
        mainW = WitnessTree(normData, maxSize, algoType, rootWitness)
        altW = WitnessTree(normData, maxSize, algoType, rootWitness)
        leafsToCalculate = FixedSizeArrayList(mainW.maxLeafCount)
        val treeExists = refineTree(mainW, mainW.root, maxSize)
        uniqueSets = setTrie.wordsStored
        setTrieSize = setTrie.numberOfNodes
        return if (treeExists) mainW.getDecisionTree() else null
    }

    private fun refineTree(w: WitnessTree, curRoot: Int, maxSize: Int): Boolean {
        searchTreeNodes++
        if (timeoutHappened) return false
        val curRemaining = maxSize - w.size[curRoot]
        if (w.isCorrectlyClassified[curRoot]) {
            return true // tree classifies data correctly
        } else if (curRemaining <= 0) {
            return false // tree has reached maximum size without classifying the data correctly
        }
        // check lower bounds
        if (algoType.useLowerBounds) {
            if (w.impLB.calcImpLowerBound(curRoot, curRemaining) > curRemaining) {
                lowerBoundEffect++
                return false
            }
        }
        // check leaf subsets
        if (algoType.useSubsetCaching && w == mainW) {
            leafsToCalculate.clear()
            for (leaf in w.maxInnerCount..w.lastAddedLeaf) {
                getWordFromLeaf(w, leaf)
                val exists = setTrie.existsSubsetValue(word, curRemaining + 1, false)
                if (exists != null) {
                    // in this case we found a subset of the examples in leaf that needs at least curRemaining + 1
                    // one-step refinements to be correctly classified
                    copiedSets++
                    return false
                } else if (w.getLeafExampleCount(leaf) <= setTrieMaxSetSize) {
                    leafsToCalculate.add(leaf)
                }
            }
            // now calculate lower bounds for the leafs in leafsToCalculate
            for (leaf in leafsToCalculate) {
                getWordFromLeaf(w, leaf)
                prepAltTree(word, word[0])
                val result = refineTree(altW, altW.root, curRemaining)
                if (!result) {
                    setTrie.insert(word, curRemaining + 1, false)
                    return false
                }
            }
        }

        val e = if (!algoType.useDirtyPriority || w != mainW || w.size[w.root] != 0) {
            w.getLowestDirtyExample(curRoot)
        } else {
            firstDirtyExample
        }
        var vChanged: Boolean
        var iChanged: Boolean
        // iterate over all vertices on the leaf to root path starting at the leaf of e
        var v = w.getLeafOf(e)
        while (v != -1) {
            // iterate over all dimensions where e and wit differ
            vChanged = true
            for (dim in 0..<normData.d) {
                val value = normData.values[e][dim]
                // check if there is a threshold that does not assign an existing witness to the new leaf
                w.updateWitnessThresholds(v)
                val thrRange = if (value < w.leftWitThr[dim][v]) {
                    value..<w.leftWitThr[dim][v]
                } else if (value > w.rightWitThr[dim][v]) {
                    (value - 1) downTo w.rightWitThr[dim][v]
                } else {
                    continue
                }
                iChanged = true
                // iterate over all thresholds that can split e and wit from each-other in dimension dim
                for (thr in thrRange) {
                    // perform one-step refinement
                    if (vChanged) {
                        w.refineTree(v, dim, thr, e)
                        vChanged = false
                        iChanged = false
                    } else if (iChanged) {
                        // a different dimension or threshold does not change the general structure of the tree
                        // except for maybe switching the left and right child
                        w.updateLastRefinement(dim, thr)
                        iChanged = false
                    } else {
                        // here we only need to update the threshold which does not change the structure of the
                        // tree at all
                        w.updateLastRefinement(thr)
                    }
                    // if a constraint has been broken we can immediately move on to the next dimension
                    // moving the threshold will just result in the same constraint being broken
                    if (algoType.useSubsetConstraints && w.isASubsetConstraintBroken) {
                        subsetConstraintEffect++
                        break
                    }
                    // recursively continue refining the tree until a good tree has been found
                    if (v == curRoot) {
                        if (refineTree(w, w.lastAddedInner, maxSize)) return true
                    } else {
                        if (refineTree(w, curRoot, maxSize)) return true
                    }
                    if (timeoutHappened) return false
                }
            }
            // We have not performed a refinement yet if vChanged is still true at this point
            if (!vChanged) {
                w.revertLastRefinement()
            }
            // choose next vertex on path to curRoot
            v = if (v != curRoot) w.parent[v] else -1
        }
        return false
    }

    // ----------------------------------------------------------------------------------------------------------------
    //                                            Helper Functions
    // ----------------------------------------------------------------------------------------------------------------

    private fun resetInfoVariables() {
        timeoutHappened = false
        searchTreeNodes = 0
        lowerBoundEffect = 0
        subsetConstraintEffect = 0
        uniqueSets = 0
        copiedSets = 0
        setTrieSize = 0
    }

    private fun calcStartingPair(): Pair<Int, Int> {
        var bestE1 = -1
        var bestE2 = -1
        var bestScore = Int.MAX_VALUE
        for (e1 in 0..<normData.n) {
            if (!normData.cla[e1]) continue
            for (e2 in 0..<normData.n) {
                if (normData.cla[e2]) continue
                val curScore = normData.values[e1].indices.sumOf { dim ->
                    abs(normData.values[e1][dim] - normData.values[e2][dim])
                }
                if (curScore < bestScore) {
                    bestE1 = e1
                    bestE2 = e2
                    bestScore = curScore
                }
            }
        }
        return Pair(bestE1, bestE2)
    }

    private fun getWordFromLeaf(w: WitnessTree, leaf: Int) {
        word.clear()
        w.eLists.forEach(w.dirtyList[leaf]) { e -> word.add(e) }
        w.eLists.forEach(w.notDirtyList[leaf]) { e -> word.add(e) }
    }

    private fun prepAltTree(exampleList: FixedSizeArrayList, newWit: Int) {
        altW = WitnessTree(normData, mainW.maxInnerCount, algoType, newWit)
        altW.reduceExamplesToSubset(exampleList)
    }
}
