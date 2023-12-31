package lowerbounds

import WitnessTree
import java.util.*

class ImpLB(
    private val w: WitnessTree,
) {

    private val maxImpSizes = Array<PriorityQueue<Int>>(w.maxVertexCount) { PriorityQueue() }
    private var maxImpSizesSum = IntArray(w.maxVertexCount)

    fun calcImpLowerBound(curRoot: Int, maxLB: Int): Int {
        // make sure the wit and dirty thresholds are up-to-date
        w.updateDirtyThresholds(curRoot)
        w.updateWitnessThresholds(curRoot)
        // calculate lower bound for examples with class true
        calcMaxImpSizesInSubtree(curRoot, curRoot, true, maxLB + 1)
        val lbTrue = maxImpSizes[curRoot].size
        if (lbTrue > maxLB) return lbTrue
        // subtract the lower bound from remaining
        val newRemaining = maxLB - lbTrue
        // calculate lower bound for examples with class false
        calcMaxImpSizesInSubtree(curRoot, curRoot, false, newRemaining + 1)
        return lbTrue + maxImpSizes[curRoot].size
    }

    /**
     * Adds [imp] to the queue that belongs to the subtree of [subRoot] and updates the corresponding sum.
     * Makes sure that the size of the queue is never greater than [remaining].
     */
    private fun addImpToQueue(imp: Int, subRoot: Int, remaining: Int) {
        maxImpSizes[subRoot].add(imp)
        maxImpSizesSum[subRoot] += imp
        if (maxImpSizes[subRoot].size > remaining) {
            removeLastImpFromQueue(subRoot)
        }
    }

    /**
     * Removes the smallest imp from the queue that belongs to the subtree of [subRoot] and updates the
     * corresponding sum.
     */
    private fun removeLastImpFromQueue(subRoot: Int) {
        maxImpSizesSum[subRoot] -= maxImpSizes[subRoot].poll()
    }

    /**
     * Calculates the number of dirty examples with class [cla] in the subtree of [subRoot] that would be assigned to
     * the new leaf created by a refinement defined by [dim], [bound] and [countLeftSide].
     * [dim] is the dimension of the refinement, [bound] is the threshold and [countLeftSide] is true iff the leaf
     * will be the left child of the new inner vertex.
     */
    private fun calcImpInSubtree(subRoot: Int, dim: Int, bound: Int, countLeftSide: Boolean, cla: Boolean): Int {
        return if (w.isLeaf(subRoot)) {
            if (w.cla[subRoot] == cla) {
                0
            } else {
                var count = 0
                w.eLists.forEach(w.dirtyList[subRoot]) { curE ->
                    if (countLeftSide && w.data.values[curE][dim] < bound) {
                        count++
                    }
                    if (!countLeftSide && w.data.values[curE][dim] > bound) {
                        count++
                    }
                }
                count
            }
        } else {
            calcImpInSubtree(w.leftChild[subRoot], dim, bound, countLeftSide, cla) +
                calcImpInSubtree(w.rightChild[subRoot], dim, bound, countLeftSide, cla)
        }
    }

    /**
     * Calculates the minimum number of refinements that are needed to correctly assign all examples with class
     * [curClass] in the subtree of [subRoot]. The number is at most [remaining].
     * @return the total number of dirty examples in the current subtree.
     */
    private fun calcMaxImpSizesInSubtree(totalRoot: Int, subRoot: Int, curClass: Boolean, remaining: Int): Int {
        var count = 0
        maxImpSizes[subRoot].clear()
        maxImpSizesSum[subRoot] = 0
        // first calculate max imp sizes for children and count the number of examples with curClass
        if (w.isLeaf(subRoot)) {
            // count dirty examples with the correct class in this leaf
            // all dirty examples will have the opposite class of the leaf
            if (w.cla[subRoot] != curClass) {
                count += w.eLists.getSize(w.dirtyList[subRoot])
            }
        } else {
            count += calcMaxImpSizesInSubtree(totalRoot, w.leftChild[subRoot], curClass, remaining)
            count += calcMaxImpSizesInSubtree(totalRoot, w.rightChild[subRoot], curClass, remaining)
        }
        // if count is 0 all improvements will be 0
        if (count == 0) return 0

        // calculate the cuts for the current tree
        for (dim in 0..<w.data.d) {
            // check if the cuts on the left or right are subsets of the respective cuts of the parent
            val calcLeft =
                (subRoot == totalRoot) || (w.leftWitThr[dim][subRoot] != w.leftWitThr[dim][w.parent[subRoot]])
            val calcRight =
                (subRoot == totalRoot) || (w.rightWitThr[dim][subRoot] != w.rightWitThr[dim][w.parent[subRoot]])
            // only calc cuts for the correct class
            if (curClass) {
                if (calcLeft && w.leftDirtyThrTrue[dim][subRoot] < w.leftWitThr[dim][subRoot]) {
                    val imp =
                        calcImpInSubtree(subRoot, dim, w.leftWitThr[dim][subRoot], countLeftSide = true, cla = true)
                    addImpToQueue(imp, subRoot, remaining)
                }
                if (calcRight && w.rightDirtyThrTrue[dim][subRoot] > w.rightWitThr[dim][subRoot]) {
                    val imp =
                        calcImpInSubtree(subRoot, dim, w.rightWitThr[dim][subRoot], countLeftSide = false, cla = true)
                    addImpToQueue(imp, subRoot, remaining)
                }
            } else {
                if (calcLeft && w.leftDirtyThrFalse[dim][subRoot] < w.leftWitThr[dim][subRoot]) {
                    val imp =
                        calcImpInSubtree(subRoot, dim, w.leftWitThr[dim][subRoot], countLeftSide = true, cla = false)
                    addImpToQueue(imp, subRoot, remaining)
                }
                if (calcRight && w.rightDirtyThrFalse[dim][subRoot] > w.rightWitThr[dim][subRoot]) {
                    val imp =
                        calcImpInSubtree(subRoot, dim, w.rightWitThr[dim][subRoot], countLeftSide = false, cla = false)
                    addImpToQueue(imp, subRoot, remaining)
                }
            }
        }
        // add imps of children unless subRoot is a leaf
        if (!w.isLeaf(subRoot)) {
            val leftQueue = maxImpSizes[w.leftChild[subRoot]]
            val rightQueue = maxImpSizes[w.rightChild[subRoot]]
            for (imp in leftQueue) {
                addImpToQueue(imp, subRoot, remaining)
            }
            leftQueue.clear()
            for (imp in rightQueue) {
                addImpToQueue(imp, subRoot, remaining)
            }
            rightQueue.clear()
        }
        // reduce the number of imps as much as possible such that the sum is still greater or equal to count
        while (maxImpSizes[subRoot].isNotEmpty() && maxImpSizesSum[subRoot] - maxImpSizes[subRoot].peek() >= count) {
            removeLastImpFromQueue(subRoot)
        }
        // make sure that the sum of the imps is not greater than count
        if (maxImpSizesSum[subRoot] > count) {
            val diff = maxImpSizesSum[subRoot] - count
            maxImpSizes[subRoot].add(maxImpSizes[subRoot].poll() - diff)
            maxImpSizesSum[subRoot] = count
        }
        return count
    }
}
