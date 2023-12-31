import datastructures.importDataFromFile

/**
 * argument format:
 *
 *    [0] data directory path: string
 *    [1] dataset name: string
 *    [2] output path: string
 *    [3] subset ratio: float
 *    [4] subset seed: int
 *    [5] s: int
 *    [6] timeout in s: long
 *    [7] problem ID: int
 *    [8] algo type id: int
 *    [9] upper bound: int
 *    [10] upper bound time in ms: int
 */
fun main(args: Array<String>) {
    // get input parameters
    val dataDirectoryPath = args[0]
    val dataSetName = args[1]
    val outputPath = args[2]
    val subsetRatio = args[3].toFloat()
    val subsetSeed = args[4].toInt()
    val s = args[5].toInt()
    val timeoutSec = args[6].toLong()
    val problemID = args[7].toInt()
    val algoType = AlgoType.getAlgoTypeFromId(args[8].toInt())
    val upperBound = args[9].toInt()
    val ubTimeMS = args[10].toInt()

    val data = importDataFromFile(dataDirectoryPath, dataSetName)
    println("Started problem $problemID")
    val result = runTestFor(
        algoType,
        dataSetName,
        data,
        subsetRatio,
        s,
        timeoutSec,
        upperBound,
    )
    printInfoCSV(outputPath, problemID, algoType.id, data.n, subsetRatio, subsetSeed, timeoutSec, result, ubTimeMS)
    println("Finished problem $problemID")
}
