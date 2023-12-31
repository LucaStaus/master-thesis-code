package lowerbounds

import datastructures.FixedSizeArrayList
import datastructures.MultiLinkedList
import datastructures.NormalizedDataSet
import kotlin.math.max
import kotlin.math.min

abstract class PairLB(
    protected val data: NormalizedDataSet,
) {

    protected val exampleIds = IntArray(data.n)
    protected val dimensionIds = IntArray(data.d)

    protected val pairCount: Int
    protected val refinementCount: Int

    protected val refinementNeighbors: Array<ArrayList<Int>>
    protected val pairNeighbors: Array<ArrayList<Int>>

    protected val existingPairs: BooleanArray
    protected val relevantPairs: BooleanArray

    init {
        // map examples to ids such that adding the ids of two examples with different classes will result
        // in a unique number for each such pair. Those numbers should also be continuous from 0 to #pairs - 1.
        val trueCount = data.cla.count { it }
        val falseCount = data.n - trueCount
        val trueExamples = FixedSizeArrayList(trueCount)
        val falseExamples = FixedSizeArrayList(falseCount)
        var curFalseId = 0
        var curTrueId = 0
        for (e in 0..<data.n) {
            if (data.cla[e]) {
                trueExamples.add(e)
                exampleIds[e] = curTrueId
                curTrueId++
            } else {
                falseExamples.add(e)
                exampleIds[e] = curFalseId * trueCount
                curFalseId++
            }
        }
        pairCount = trueCount * falseCount
        // map dimensions to ids such that adding the id of a dimension and a threshold in that dimension together
        // will result in a unique number for each one-step refinement. Those numbers should be continuous from 0
        // to #refinements - 1.
        var sum = 0
        for (dim in 0..<data.d) {
            dimensionIds[dim] = sum
            sum += data.dSizes[dim]
        }
        refinementCount = sum

        // initialize variables
        refinementNeighbors = Array(refinementCount) { ArrayList() }
        pairNeighbors = Array(pairCount) { ArrayList() }
        existingPairs = BooleanArray(pairCount) { false }
        relevantPairs = BooleanArray(pairCount) { true }

        for (eT in trueExamples) {
            for (eF in falseExamples) {
                val pairId = getPairId(eT, eF)
                // iterate over all one-step refinements that can split the pair (eT,eF)
                for (dim in 0..<data.d) {
                    val lower = min(data.values[eT][dim], data.values[eF][dim])
                    val higher = max(data.values[eT][dim], data.values[eF][dim])
                    for (thr in lower..<higher) {
                        val refinementId = getRefinementId(dim, thr)
                        pairNeighbors[pairId].add(refinementId)
                        refinementNeighbors[refinementId].add(pairId)
                    }
                }
            }
        }
        // removeSupersetPairs()
    }

    private fun removeSupersetPairs() {
        val supersetCandidates = MultiLinkedList(2, pairCount, 0)
        for (pairId in 0..<pairCount) {
            supersetCandidates.moveList(1, 0)
            // check if a pair exists with a neighborhood that is a superset of the neighborhood of pairId
            val smallestNeighbor = pairNeighbors[pairId].minBy { refinementNeighbors[it].size }
            for (n in refinementNeighbors[smallestNeighbor]) {
                if (n == pairId || !relevantPairs[n]) continue
                supersetCandidates.move(n, 1)
            }
            for (refId in pairNeighbors[pairId]) {
                // eliminate all candidates that don't have refId as a neighbor
                val iter = supersetCandidates.getIterator(1)
                for (candidate in iter) {
                    if (refId !in pairNeighbors[candidate]) {
                        iter.moveCurElem(0)
                    }
                }
            }
            supersetCandidates.forEach(1) {
                relevantPairs[it] = false
            }
        }
        println(relevantPairs.count { !it })
        println(pairCount)
    }

    protected fun getPairId(e1: Int, e2: Int): Int {
        return exampleIds[e1] + exampleIds[e2]
    }

    protected fun getRefinementId(dim: Int, thr: Int): Int {
        return dimensionIds[dim] + thr
    }

    open fun removePair(e1: Int, e2: Int) {
        val id = getPairId(e1, e2)
        if (relevantPairs[id]) {
            existingPairs[id] = false
        }
    }

    open fun addPair(e1: Int, e2: Int) {
        val id = getPairId(e1, e2)
        if (relevantPairs[id]) {
            existingPairs[id] = true
        }
    }

    abstract fun calcLowerBound(maxLB: Int): Int
}
