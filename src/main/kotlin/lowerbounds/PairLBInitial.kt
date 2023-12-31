package lowerbounds

import datastructures.NormalizedDataSet
import gurobi.*
import kotlin.math.ceil

class PairLBInitial(
    env: GRBEnv,
    data: NormalizedDataSet,
) : PairLB(data) {

    private val delta = 0.00001

    private val model = GRBModel(env)
    private val variables: Array<GRBVar>
    private val pairLinExpr: Array<GRBLinExpr>
    private val addedPairsLastUpdate: BooleanArray

    init {
        val obj = GRBLinExpr()
        // create a variable for each one-step refinement and add them to the model
        variables = Array(refinementCount) { refinementId ->
            val variable = model.addVar(0.0, 1.0, 1.0, GRB.CONTINUOUS, "$refinementId")
            obj.addTerm(1.0, variable)
            variable
        }
        model.update()
        model.setObjective(obj, GRB.MINIMIZE)
        model.update()

        addedPairsLastUpdate = BooleanArray(pairCount) { false }
        // create a linear expression and a constraint for each pair of examples with different classes
        pairLinExpr = Array(pairCount) { GRBLinExpr() }
        for (pairId in 0..<pairCount) {
            // create the linear expression
            val linExpr = pairLinExpr[pairId]
            // add variables of one-step refinements to the linear expression that split the pair
            for (refinementId in pairNeighbors[pairId]) {
                linExpr.addTerm(1.0, variables[refinementId])
            }
            model.addConstr(pairLinExpr[pairId], GRB.GREATER_EQUAL, 1.0, "$pairId")
        }
    }

    override fun calcLowerBound(maxLB: Int): Int {
        model.optimize()
        val min = ceil(model.get(GRB.DoubleAttr.ObjVal) - delta)
        return min.toInt()
    }

    fun dispose() {
        model.dispose()
    }
}
