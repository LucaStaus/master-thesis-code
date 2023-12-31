import datastructures.DataSet
import datastructures.DecisionTree
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.timerTask
import kotlin.math.max
import kotlin.system.measureTimeMillis

const val resultsDir = "../Experimente/Ergebnisse"
const val instanceFileDir = "../Experimente/Instanzen"
const val dataDir = "../Daten"

val dataSets = arrayOf(
    "appendicitis",
    "australian",
    "auto",
    "backache",
    "biomed",
    "breast-cancer",
    "bupa",
    "cars",
    "cleve",
    "cleveland",
    "cleveland-nominal",
    "cloud",
    "colic",
    "contraceptive",
    "dermatology",
    "diabetes",
    "ecoli",
    "glass",
    "glass2",
    "haberman",
    "hayes-roth",
    "heart-c",
    "heart-h",
    "heart-statlog",
    "hepatitis",
    "hungarian",
    "lupus",
    "lymphography",
    "molecular_biology_promoters",
    "new-thyroid",
    "postoperative-patient-data",
    "schizo",
    "soybean",
    "spect",
    "tae",
)

enum class AlgoType(
    val strategy: Int,
    val useDirtyPriority: Boolean,
    val doPreProcessing: Boolean,
    val useLowerBounds: Boolean,
    val useSubsetConstraints: Boolean,
    val useSubsetCaching: Boolean,
) {
    BASIC(
        1,
        false,
        false,
        false,
        false,
        false,
    ),
    IMP1(
        1,
        true,
        false,
        false,
        false,
        false,
    ),
    IMP2(
        1,
        true,
        true,
        false,
        false,
        false,
    ),
    IMP3(
        1,
        true,
        true,
        true,
        false,
        false,
    ),
    IMP4(
        1,
        true,
        true,
        true,
        true,
        false,
    ),
    STRATEGY1(
        1,
        true,
        true,
        true,
        true,
        true,
    ),
    STRATEGY2(
        2,
        true,
        true,
        true,
        true,
        true,
    ),
    STRATEGY3(
        3,
        true,
        true,
        true,
        true,
        true,
    ),
    DECISION(
        0,
        true,
        true,
        true,
        true,
        true,
    ),
    ;

    val id: Int
        get() = ordinal

    companion object {
        fun getAlgoTypeFromId(id: Int): AlgoType {
            if (id in 0..<entries.size) {
                return entries[id]
            }
            throw NoSuchElementException("The id $id does not belong to any algorithm.")
        }
    }
}

class TestResult(
    val id: Int,
    val dataSetName: String,
    val algoType: AlgoType,
    val subsetRatio: Float,
    val d: Int,
    val s: Int,
    val foundTree: Boolean,
    val treeSize: Int,
    val timeMS: Long,
    val memoryMiB: Long,
    val timeoutHappened: Boolean,
    val correctTrainingData: Float,
    val searchTreeNodes: Int,
    val lowerBoundEffect: Int,
    val subsetConstraintEffect: Int,
    val uniqueSets: Int,
    val copiedSets: Int,
    val setTrieSize: Int,
) {
    companion object {
        val empty = TestResult(
            -1, "", AlgoType.DECISION, 0f, 0, 0, false, 0, 0,
            0, false, 0f, 0,
            0, 0, 0, 0, 0,
        )
    }
}

/**
 * Columns of the output file:
 *
 *    [00] problem ID: Int
 *    [01] algo type ID: Int
 *    [02] dataset name: String
 *    [03] dataset size: Int
 *    [04] subset ratio: Float
 *    [05] subset seed: Int
 *    [06] # of dimensions: Int
 *    [07] s: Int
 *    [08] timeoutSec: Long
 *    [09] timeMS: Long
 *    [10] memoryMiB: Long
 *    [11] timeout: Boolean
 *    [12] found tree: Boolean
 *    [13] tree size: Int
 *    [14] correct training data: Float
 *    [15] # of search tree nodes: Int
 *    [16] lower bound effect: Int
 *    [17] subset constraint effect: Int
 *    [18] unique sets: Int
 *    [19] copied sets: Int
 *    [20] setTrie size: Int
 */
fun printInfoCSV(
    outputPath: String,
    problemID: Int,
    algoId: Int,
    dataSetSize: Int,
    subsetRatio: Float,
    subsetSeed: Int,
    timeoutSec: Long,
    testResult: TestResult,
    ubTimeMS: Int,
) {
    val builder = StringBuilder()
    builder.append("$problemID;")
    builder.append("$algoId;")
    builder.append("${testResult.dataSetName};")
    builder.append("$dataSetSize;")
    builder.append("$subsetRatio;")
    builder.append("$subsetSeed;")
    builder.append("${testResult.d};")
    builder.append("${testResult.s};")
    builder.append("$timeoutSec;")
    builder.append("${testResult.timeMS + ubTimeMS};")
    builder.append("${testResult.memoryMiB};")
    builder.append("${testResult.timeoutHappened};")
    builder.append("${testResult.foundTree};")
    builder.append("${testResult.treeSize};")
    builder.append("${String.format("%.2f", testResult.correctTrainingData)};")
    builder.append("${testResult.searchTreeNodes};")
    builder.append("${testResult.lowerBoundEffect};")
    builder.append("${testResult.subsetConstraintEffect};")
    builder.append("${testResult.uniqueSets};")
    builder.append("${testResult.copiedSets};")
    builder.append("${testResult.setTrieSize}")

    val out = PrintStream(FileOutputStream(File(outputPath), true))
    out.println(builder.toString())
    out.close()
}

fun readResultsFromCSVFiles(resultFilePrefix: String): List<TestResult> {
    val results = LinkedList<TestResult>()
    for (f in File(resultsDir).listFiles()!!) {
        if (!f.name.startsWith(resultFilePrefix)) {
            continue
        } else {
            f.forEachLine { l ->
                val splits = l.split(";")
                results.add(
                    TestResult(
                        splits[0].toInt(),
                        splits[2],
                        AlgoType.getAlgoTypeFromId(splits[1].toInt()),
                        splits[4].toFloat(),
                        splits[6].toInt(),
                        splits[7].toInt(),
                        splits[12].toBoolean(),
                        splits[13].toInt(),
                        splits[9].toLong(),
                        splits[10].toLong(),
                        splits[11].toBoolean(),
                        splits[14].toFloat(),
                        splits[15].toInt(),
                        splits[16].toInt(),
                        splits[17].toInt(),
                        splits[18].toInt(),
                        splits[19].toInt(),
                        splits[20].toInt(),
                    ),
                )
            }
        }
    }
    return results
}

fun runTestFor(
    algoType: AlgoType,
    dataSetName: String,
    trainingData: DataSet,
    subsetRatio: Float,
    maxSize: Int,
    timeoutSec: Long,
    upperBound: Int,
): TestResult {
    val algo = WitnessTreeAlgo(
        trainingData,
        algoType,
    )
    var tree: DecisionTree? = null
    var timeMS = 0L
    val (timer, timeoutTask) = scheduleTimeout(algo, timeoutSec)
    val memoryMiB = measureMemoryMiB {
        timeMS = measureTimeMillis {
            tree = algo.findTree(maxSize, upperBound)
        }
    }
    val timeoutHappened = !timeoutTask.cancel()
    timer.cancel()
    val correctTrainingData = tree?.let { testClassification(it, trainingData) } ?: 0f
    return TestResult(
        -1,
        dataSetName,
        algoType,
        subsetRatio,
        trainingData.d,
        if (algoType.strategy > 0) -1 else maxSize,
        tree != null,
        tree?.innerCount ?: -1,
        timeMS,
        memoryMiB,
        timeoutHappened,
        correctTrainingData,
        algo.searchTreeNodes,
        algo.lowerBoundEffect,
        algo.subsetConstraintEffect,
        algo.uniqueSets,
        algo.copiedSets,
        algo.setTrieSize,
    )
}

fun testClassification(t: DecisionTree, data: DataSet): Float {
    var countCorrectData = 0
    for (e in 0..<data.n) {
        val c = t.classifyExample(data.values[e])
        if (c == data.cla[e]) {
            countCorrectData++
        }
    }
    return countCorrectData.toFloat() / data.n.toFloat()
}

fun printInfoConsole(
    dataSetSize: Int,
    subsetSeed: Int,
    timeoutSec: Long,
    testResult: TestResult,
) {
    println("Dataset:           ${testResult.dataSetName}")
    println("Total Examples:    $dataSetSize")
    println("Training Examples: ${testResult.subsetRatio}")
    println("Dimensions:        ${testResult.d}")
    println("Max Tree Size:     ${testResult.s}")
    println()
    if (testResult.timeoutHappened) {
        println("Timeout after $timeoutSec seconds.")
    } else {
        if (testResult.foundTree) {
            println("Tree found!")
            println("Size:                  ${testResult.treeSize}")
            println("Correct Training Data: ${testResult.correctTrainingData}")
        } else {
            println("Tree not found!")
        }
        println("Time:   ${prettyTime(testResult.timeMS)}")
        println("Memory: ${testResult.memoryMiB}MiB")

        println("Search Tree:   ${testResult.searchTreeNodes}")
        println("LB Effect:     ${testResult.lowerBoundEffect}")

        println("Unique Sets:   ${testResult.uniqueSets}")
        println("Copied Sets:   ${testResult.copiedSets}")
        println("SetTrie Size:  ${testResult.setTrieSize}")
    }
}

fun createTestReport(reportName: String, results: List<TestResult>) {
    // create new file
    val cal = Calendar.getInstance()
    val sdf = SimpleDateFormat("yyyy-MM-dd")
    val curTime = sdf.format(cal.time)
    var foundName = false
    var num = 0
    var outputFile = File("reports/debug.txt")
    while (!foundName) {
        outputFile = File("reports/Report_${curTime}_${reportName}_$num.txt")
        if (outputFile.exists()) {
            num++
        } else {
            outputFile.createNewFile()
            foundName = true
        }
    }
    val out = PrintStream(outputFile)
    // gather some general data for formatting the output
    val maxMemoryLength = results.maxOf { it.memoryMiB.toString().length }
    val maxSearchTreeNodesLength = results.maxOf { it.searchTreeNodes.toString().length }
    // print results
    // group results by the used data set
    results.groupBy { it.dataSetName }.toSortedMap().forEach { (dataSetName, l) ->
        out.println("Results for $dataSetName (d = ${l.first().d}):")
        // now group and sort results by the size of the used subset
        l.groupBy { it.subsetRatio }.toSortedMap().forEach { (n, l2) ->
            // now group and sort results by the parameter h
            l2.groupBy { it.s }.toSortedMap().forEach { (s, resL) ->
                out.print("# Instances = %2d, n = %4d, s = %2d: ".format(resL.size, n, s))
                if (resL.all { it.timeoutHappened }) {
                    out.println("Timeout for all Instances")
                } else {
                    val resLFiltered = resL.filter { !it.timeoutHappened }
                    if (resLFiltered.any { it.foundTree && it.correctTrainingData != 1.0f }) throw Exception("Not 1")
                    val timeMSAvg = resLFiltered.sumOf { it.timeMS } / resLFiltered.size
                    val timeMSWorst = resLFiltered.maxOf { it.timeMS }
                    val memoryMiBAvg = resLFiltered.sumOf { it.memoryMiB } / resLFiltered.size
                    out.print("${prettyTime(timeMSAvg)} ${prettyTime(timeMSWorst)} ")
                    out.print("%${maxMemoryLength}dMiB ".format(memoryMiBAvg))
                    out.print("Timeout: ${resL.count { it.timeoutHappened }}/${resL.size} ")
                    out.print("Tree found: ${resLFiltered.count { it.foundTree }}/${resLFiltered.size} ")
                    out.print("Search tree nodes: %${maxSearchTreeNodesLength}d ".format(resLFiltered.sumOf { it.searchTreeNodes } / resLFiltered.size))
                    out.println("LB: ${resLFiltered[0].lowerBoundEffect} ")
                    // out.print("Unique: ${resLFiltered[0].uniqueSets},")
                    // out.print("Copied: ${resLFiltered[0].copiedSets},")
                    // out.println("Median: ${resLFiltered[0].setSizeMedian}")
                }
            }
        }
    }
    out.close()
}

fun scheduleTimeout(algo: WitnessTreeAlgo, timeoutSec: Long): Pair<Timer, TimerTask> {
    algo.timeoutHappened = false
    val timer = Timer()
    return Pair(
        timer,
        timer.schedule(timeoutSec * 1000) {
            algo.timeoutHappened = true
        },
    )
}

fun prettyTime(millis: Long): String {
    var h = 0L
    var m = 0L
    var s = 0L
    var ms = millis
    if (ms >= 3600000L) {
        h = ms / 3600000L
        ms %= 3600000L
    }
    if (ms >= 60000L) {
        m = ms / 60000L
        ms %= 60000L
    }
    if (ms >= 1000L) {
        s = ms / 1000L
        ms %= 1000L
    }
    return "%d:%02d:%02d.%03d".format(h, m, s, ms)
}

fun measureMemoryMiB(task: () -> Unit): Long {
    val runtime = Runtime.getRuntime()
    var memory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val timer = Timer()
    timer.scheduleAtFixedRate(
        timerTask {
            memory = max(memory, (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024))
        },
        0,
        100,
    )
    task()
    timer.cancel()
    return max(memory, (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024))
}
