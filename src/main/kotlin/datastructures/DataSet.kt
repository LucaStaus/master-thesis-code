package datastructures

import dataDir
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintStream
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class DataSet(
    /**
     * The number of examples.
     */
    val n: Int,
    /**
     * The number of dimensions.
     */
    val d: Int,
    /**
     * Should have size n x d.
     * Each entry (e, i) represents the value of example e in dimension i.
     */
    val values: Array<DoubleArray>,
    /**
     * Should have size n.
     * Each entry (e) represents the class that is assigned to example e.
     */
    val cla: BooleanArray,
) {

    fun normalize(): NormalizedDataSet {
        val uniqueValuesSet = HashSet<Double>()
        val allValues = Array(d) { DoubleArray(0) }
        val maps = Array(d) { dim ->
            uniqueValuesSet.clear()
            for (e in 0..<n) {
                uniqueValuesSet.add(values[e][dim])
            }
            val listAll = LinkedList<Double>()
            listAll.addAll(uniqueValuesSet)
            listAll.sort()
            allValues[dim] = listAll.toDoubleArray()
            val map = HashMap<Double, Int>()
            listAll.forEachIndexed { i, v -> map[v] = i }
            map
        }
        val normalData = NormalizedDataSet(
            n,
            d,
            Array(n) { e ->
                IntArray(d) { dim ->
                    maps[dim][values[e][dim]]!!
                }
            },
            cla,
            IntArray(d) { dim -> allValues[dim].size },
            Array(d) { dim ->
                Array(allValues[dim].size - 1) { thr ->
                    Pair(dim, allValues[dim][thr])
                }
            },
            IntArray(n) { it },
        )
        return normalData
    }

    fun reduceAndNormalize(): NormalizedDataSet {
        val thresholds = HashMap<Int, LinkedList<Double>>()
        val thresholdsPlus = HashMap<Int, LinkedList<Double>>()
        val adjustedValues = HashMap<Int, DoubleArray>()
        val uniqueValuesSet = HashSet<Double>()
        for (dim in 0..<d) {
            val examples = DoubleArray(n)
            for (e in 0..<n) {
                examples[e] = values[e][dim]
                uniqueValuesSet.add(values[e][dim])
            }
            adjustedValues[dim] = examples
            val list = LinkedList<Double>()
            val listPlus = LinkedList<Double>()
            list.addAll(uniqueValuesSet)
            listPlus.addAll(uniqueValuesSet)
            list.sort()
            listPlus.sortDescending()
            list.removeLast()
            listPlus.removeLast()
            thresholds[dim] = list
            thresholdsPlus[dim] = listPlus
            uniqueValuesSet.clear()
        }

        // find cuts that can be removed
        val ex = IntArray(n) { it }.toMutableList()
        for (dim in 0..<d) {
            val adjusted = adjustedValues[dim]!!
            ex.sortBy { adjusted[it] }
            val unique = thresholds[dim]!!
            val uniquePlus = thresholdsPlus[dim]!!
            if (unique.isEmpty()) continue
            // check left side
            var curExampleI = 0
            var falseCount = 0
            var trueCount = 0
            var bestThreshold = unique.first
            for (thr in unique) {
                if (thr == unique.first) continue
                while (adjusted[ex[curExampleI]] <= thr) {
                    if (cla[ex[curExampleI]]) trueCount++ else falseCount++
                    curExampleI++
                }
                if (trueCount != 0 && falseCount != 0) break
                bestThreshold = thr
            }
            for (e in ex) {
                if (adjusted[e] < bestThreshold) {
                    adjusted[e] = bestThreshold
                } else {
                    break
                }
            }
            while (unique.first < bestThreshold) {
                unique.removeFirst()
                uniquePlus.removeLast()
            }
            ex.sortByDescending { adjusted[it] }
            // check right side
            curExampleI = 0
            falseCount = 0
            trueCount = 0
            bestThreshold = uniquePlus.first
            for (thr in uniquePlus) {
                if (thr == uniquePlus.first) continue
                while (adjusted[ex[curExampleI]] >= thr) {
                    if (cla[ex[curExampleI]]) trueCount++ else falseCount++
                    curExampleI++
                }
                if (trueCount != 0 && falseCount != 0) break
                bestThreshold = thr
            }
            for (e in ex) {
                if (adjusted[e] > bestThreshold) {
                    adjusted[e] = bestThreshold
                } else {
                    break
                }
            }
            while (uniquePlus.first > bestThreshold) {
                unique.removeLast()
                uniquePlus.removeFirst()
            }
        }
        // remove equal cuts
        val left = BooleanArray(n)
        for (dim1 in 0..<d - 1) {
            val adjusted = adjustedValues[dim1]!!
            ex.sortBy { adjusted[it] }
            val thrIter = thresholds[dim1]!!.iterator()
            val thrPlusIter = thresholdsPlus[dim1]!!.descendingIterator()
            thr1@ for (thr1 in thrIter) {
                val thr1Plus = thrPlusIter.next()
                for (e in 0..<n) {
                    left[e] = (adjusted[e] <= thr1)
                }
                for (dim2 in (dim1 + 1)..<d) {
                    for (thr2 in thresholds[dim2]!!) {
                        var isEqual = true
                        // check if the second cut has the exact same examples on the left side
                        for (e in 0..<n) {
                            if (left[e] != (adjustedValues[dim2]!![e] <= thr2)) {
                                isEqual = false
                                break
                            }
                        }
                        if (isEqual) {
                            // remove the cut (dim1,thr1)
                            for (e in 0..<n) {
                                if (adjusted[e] == thr1) {
                                    adjusted[e] = thr1Plus
                                }
                            }
                            thrIter.remove()
                            thrPlusIter.remove()
                            continue@thr1
                        }
                    }
                }
            }
        }

        // merge dimensions
        val originalCut = HashMap<Pair<Int, Double>, Pair<Int, Double>>()
        var nextNewDim = d
        val finalDims = LinkedList<Int>()
        val dimsToCheck = LinkedList<Int>()
        for (dim in 0..<d) dimsToCheck.add(dim)
        // finalDims.addAll(dimsToCheck)
        dimsToCheck.sortBy { thresholds[it]!!.size }
        dim1@ while (dimsToCheck.isNotEmpty()) {
            val dim1 = dimsToCheck.removeFirst()
            val adjusted1 = adjustedValues[dim1]!!
            val dim2Iter = dimsToCheck.iterator()
            for (dim2 in dim2Iter) {
                val adjusted2 = adjustedValues[dim2]!!
                ex.sortWith { e1: Int, e2: Int ->
                    if (adjusted1[e1] < adjusted1[e2]) {
                        -1
                    } else if (adjusted1[e1] > adjusted1[e2]) {
                        1
                    } else {
                        if (adjusted2[e1] < adjusted2[e2]) -1 else if (adjusted2[e1] > adjusted2[e2]) 1 else 0
                    }
                }
                var allInOrder = true
                for (i in 1..<ex.size) {
                    val e1 = ex[i - 1]
                    val e2 = ex[i]
                    if (adjusted1[e1] > adjusted1[e2] || adjusted2[e1] > adjusted2[e2]) {
                        allInOrder = false
                        break
                    }
                }
                if (allInOrder) {
                    // remove dim1 and dim2 and add new combined dimension
                    dim2Iter.remove()
                    var curValue = 0.0
                    var prevD1 = adjusted1[ex[0]]
                    var prevD2 = adjusted2[ex[0]]
                    val adjustedNew = DoubleArray(n)
                    adjustedValues[nextNewDim] = adjustedNew
                    for (ei in 0..<n) {
                        val curD1 = adjusted1[ex[ei]]
                        val curD2 = adjusted2[ex[ei]]
                        // check if a cut is required
                        if (prevD1 != curD1) {
                            var oCut = Pair(dim1, prevD1)
                            while (oCut.first >= d) oCut = originalCut[oCut]!!
                            originalCut[Pair(nextNewDim, curValue)] = oCut
                            curValue++
                        } else if (prevD2 != curD2) {
                            var oCut = Pair(dim2, prevD2)
                            while (oCut.first >= d) oCut = originalCut[oCut]!!
                            originalCut[Pair(nextNewDim, curValue)] = oCut
                            curValue++
                        }
                        prevD1 = curD1
                        prevD2 = curD2
                        adjustedNew[ex[ei]] = curValue
                    }
                    val list = LinkedList<Double>()
                    val listPlus = LinkedList<Double>()
                    for (v in 0..curValue.toInt()) {
                        list.add(v.toDouble())
                        listPlus.add(v.toDouble())
                    }
                    list.sort()
                    listPlus.sortDescending()
                    list.removeLast()
                    listPlus.removeLast()
                    thresholds[nextNewDim] = list
                    thresholdsPlus[nextNewDim] = listPlus
                    uniqueValuesSet.clear()
                    dimsToCheck.add(nextNewDim)
                    nextNewDim++
                    continue@dim1
                }
            }
            finalDims.add(dim1)
        }

        finalDims.sort()
        // remove dimensions where all examples have the same value
        finalDims.removeAll { dim -> thresholds[dim]!!.isEmpty() }
        val finalDimsArray = finalDims.toIntArray()
        val newD = finalDimsArray.size
        val allValues = HashMap<Int, DoubleArray>()

        val maps = Array(newD) { dim ->
            val oldDim = finalDimsArray[dim]
            uniqueValuesSet.clear()
            for (e in 0..<n) {
                uniqueValuesSet.add(adjustedValues[oldDim]!![e])
            }
            val listAll = LinkedList<Double>()
            listAll.addAll(uniqueValuesSet)
            listAll.sort()
            allValues[oldDim] = listAll.toDoubleArray()
            val map = HashMap<Double, Int>()
            listAll.forEachIndexed { i, v -> map[v] = i }
            map
        }
        // remove duplicate examples
        val removed = BooleanArray(n)
        e1@ for (e1 in 0..<n) {
            for (e2 in (e1 + 1)..<n) {
                // check if values are the same
                var same = true
                for (d in finalDims) {
                    if (adjustedValues[d]!![e1] != adjustedValues[d]!![e2]) {
                        same = false
                        break
                    }
                }
                if (same) {
                    removed[e1] = true
                    continue@e1
                }
            }
        }
        val newN = removed.count { !it }
        var curE = -1
        val newToOldExamples = IntArray(newN) {
            curE++
            while (curE < n && removed[curE]) curE++
            curE
        }

        val normData = NormalizedDataSet(
            newN,
            newD,
            Array(newN) { newE ->
                IntArray(newD) { dim ->
                    val oldDim = finalDimsArray[dim]
                    val oldValue = adjustedValues[oldDim]!![newToOldExamples[newE]]
                    maps[dim][oldValue]!!
                }
            },
            BooleanArray(newN) { newE -> cla[newToOldExamples[newE]] },
            IntArray(newD) { dim -> allValues[finalDimsArray[dim]]!!.size },
            Array(newD) { dim ->
                Array(allValues[finalDimsArray[dim]]!!.size - 1) { thr ->
                    val oldDim = finalDimsArray[dim]
                    val oldPair = Pair(oldDim, allValues[oldDim]!![thr])
                    if (oldDim >= d) {
                        originalCut[oldPair]!!
                    } else {
                        oldPair
                    }
                }
            },
            newToOldExamples,
        )
        return normData
    }

    fun randomSubset(size: Int, seed: Int): Pair<DataSet, DataSet> {
        val subset = DataSet(
            n = size,
            d = d,
            values = Array(size) { DoubleArray(0) },
            cla = BooleanArray(size),
        )
        val rest = DataSet(
            n = n - size,
            d = d,
            values = Array(n - size) { DoubleArray(0) },
            cla = BooleanArray(n - size),
        )
        val r = Random(seed)
        var remaining = size
        var restIndex = 0
        // algo from https://stackoverflow.com/questions/136474/best-way-to-pick-a-random-subset-from-a-collection
        for (e in 0..<n) {
            if (r.nextInt(0, Int.MAX_VALUE) % (n - e) < remaining) {
                subset.values[size - remaining] = values[e]
                subset.cla[size - remaining] = cla[e]
                remaining--
            } else {
                rest.values[restIndex] = values[e]
                rest.cla[restIndex] = cla[e]
                restIndex++
            }
        }
        return Pair(subset, rest)
    }

    override fun toString(): String {
        val result = StringBuilder()
        for (e in 0..<n) {
            result.append("E$e ${cla[e]}: ${values[e].contentToString()}\n")
        }
        return result.toString()
    }
}

class NormalizedDataSet(
    /**
     * The number of examples.
     */
    val n: Int,
    /**
     * The number of dimensions.
     */
    val d: Int,
    /**
     * Should have size n x d.
     * Each entry (e, i) represents the value of example e in dimension i.
     */
    val values: Array<IntArray>,
    /**
     * Should have size n.
     * Each entry (e) represents the class that is assigned to example e.
     */
    val cla: BooleanArray,
    /**
     * Should have size d.
     * Each entry (i) represents the number of unique values in dimension i.
     */
    val dSizes: IntArray,
    /**
     * Should have size d. The size of each inner array can be different.
     * Each entry (i, v) represents the original unnormalized cut corresponding to the cut (i, v).
     */
    val conversion: Array<Array<Pair<Int, Double>>>,
    /**
     * Should have size n.
     * Each entry (e) represents the original example that e corresponds to.
     */
    val exampleMap: IntArray,
) {

    fun getNumberOfCuts() = dSizes.sumOf { it - 1 }

    fun getDelta(): Int {
        var curMax = 0
        for (e1 in 0..<n) {
            for (e2 in (e1 + 1)..<n) {
                curMax = max((0..<d).count { values[e1][it] != values[e2][it] }, curMax)
            }
        }
        return curMax
    }

    fun getMaxCuts(): Int {
        var curMax = 0
        for (e1 in 0..<n) {
            for (e2 in (e1 + 1)..<n) {
                curMax = max(curMax, (0..<d).sumOf { abs(values[e1][it] - values[e2][it]) })
            }
        }
        return curMax
    }

    fun getBigD() = dSizes.max()

    override fun toString(): String {
        val result = StringBuilder()
        for (e in 0..<n) {
            result.append("E$e ${cla[e]}: ${values[e].contentToString()}\n")
        }
        return result.toString()
    }
}

enum class DataType {
    REAL, INTEGER
}

fun generateRandomData(n: Int, dLower: DoubleArray, dUpper: DoubleArray, dTypes: Array<DataType>, seed: Int): DataSet {
    val r = Random(seed)
    val d = dLower.size
    return DataSet(
        n = n,
        d = d,
        values = Array(n) {
            DoubleArray(d) { i ->
                when (dTypes[i]) {
                    DataType.REAL -> r.nextDouble(dLower[i], dUpper[i])
                    DataType.INTEGER -> r.nextInt(dLower[i].toInt(), dUpper[i].toInt()).toDouble()
                }
            }
        },
        cla = BooleanArray(n) { r.nextBoolean() },
    )
}

fun generateRandomData(
    n: Int,
    dLower: DoubleArray,
    dUpper: DoubleArray,
    dTypes: Array<DataType>,
    seed: Int,
    t: DecisionTree,
): DataSet {
    val r = Random(seed)
    val d = dLower.size
    val data = DataSet(
        n = n,
        d = d,
        values = Array(n) { DoubleArray(d) },
        cla = BooleanArray(n),
    )

    for (e in 0..<n) {
        var curRoot = t.root
        val lo = dLower.clone()
        val up = dUpper.clone()
        while (!t.isLeaf(curRoot)) {
            val dim = t.dim[curRoot]
            if (r.nextBoolean()) {
                // go left
                up[dim] = min(up[dim], t.thr[curRoot])
                curRoot = t.leftChild[curRoot]
            } else {
                // go right
                lo[dim] = max(lo[dim], t.thr[curRoot])
                curRoot = t.rightChild[curRoot]
            }
        }
        for (dim in 0..<d) {
            data.values[e][dim] = when (dTypes[dim]) {
                DataType.REAL -> -r.nextDouble(-up[dim], -lo[dim])
                DataType.INTEGER -> -r.nextInt(-up[dim].toInt(), -lo[dim].toInt()).toDouble()
            }
        }
        data.cla[e] = t.cla[curRoot]
    }

    return data
}

fun exportDataToFile(data: DataSet, name: String) {
    val out = PrintStream(File("$dataDir/$name.csv"))
    for (i in 1..data.d) {
        out.print("$i,")
    }
    out.println("x")
    for (e in 0..<data.n) {
        for (i in 0..<data.d) {
            if (data.values[e][i].toInt().toDouble() == data.values[e][i]) {
                out.print("${data.values[e][i].toInt()},")
            } else {
                out.print("${data.values[e][i]},")
            }
        }
        out.println(if (data.cla[e]) "1" else "0")
    }
    out.close()
}

fun importDataFromFile(dataDirectoryPath: String, name: String): DataSet {
    val file = File("$dataDirectoryPath/$name")
    val br = BufferedReader(InputStreamReader(file.inputStream()))
    val lines = LinkedList<String>()
    br.forEachLine {
        lines.add(it)
    }
    lines.removeFirst() // remove first line comment
    val n = lines.size
    val d = lines.first.split(",").size - 1
    val data = DataSet(
        n = n,
        d = d,
        values = Array(n) { DoubleArray(d) },
        cla = BooleanArray(n),
    )
    for ((e, line) in lines.withIndex()) {
        val splits = line.split(",")
        for (i in 0..<splits.size - 1) {
            data.values[e][i] = splits[i].toDouble()
        }
        data.cla[e] = splits[splits.size - 1].toInt() == 1
    }
    return data
}
