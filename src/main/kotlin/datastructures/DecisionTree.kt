package datastructures

class DecisionTree(
    size: Int,
) {

    val innerCount = size
    val leafCount = size + 1
    val vertexCount = innerCount + leafCount

    var root = -1
    val parent = IntArray(vertexCount) { -1 }
    val leftChild = IntArray(vertexCount) { -1 }
    val rightChild = IntArray(vertexCount) { -1 }

    val dim = IntArray(vertexCount) { -1 }
    val thr = DoubleArray(vertexCount) { -1.0 }
    val cla = BooleanArray(vertexCount) { false }

    /**
     * Returns True iff [vertex] is a leaf.
     */
    fun isLeaf(vertex: Int) = vertex >= innerCount

    /**
     * Decides the class for the given example [e]. The given example should have the same number of dimensions as
     * the data used to create this tree.
     */
    fun classifyExample(e: DoubleArray) = cla[getLeafOfExample(e)]

    /**
     * Returns the leaf that the given example [e] is assigned to in the subtree of [subRoot]. The given example
     * should have the same number of dimensions as the data used to create this tree.
     */
    fun getLeafOfExample(e: DoubleArray, subRoot: Int = root): Int {
        var curRoot = subRoot
        while (!isLeaf(curRoot)) {
            curRoot = if (e[dim[curRoot]] <= thr[curRoot]) {
                leftChild[curRoot]
            } else {
                rightChild[curRoot]
            }
        }
        return curRoot
    }
}
