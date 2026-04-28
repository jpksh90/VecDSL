import java.io.File
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import prettyPrintAst // Import prettyPrintAst from Main.kt if not already available

interface Lattice<T> {
    fun bottom(): T
    fun join(a: T, b: T): T
    fun lessThanOrEqual(a: T, b: T): Boolean
}

// Generic dataflow analysis framework
fun <T> runDataflow(
    cfg: CFG,
    lattice: Lattice<T>,
    transfer: (Int, T) -> T,
    direction: Direction = Direction.FORWARD
): List<T> {
    val n = cfg.nodes.size
    val state = MutableList(n) { lattice.bottom() }
    var changed: Boolean
    do {
        changed = false
        val indices = if (direction == Direction.FORWARD) 0 until n else (n - 1 downTo 0)
        for (i in indices) {
            val node = cfg.nodes[i]
            val inStates = if (direction == Direction.FORWARD) node.preds.map { state[it] } else node.succs.map { state[it] }
            val inState = inStates.fold(lattice.bottom()) { acc, s -> lattice.join(acc, s) }
            val outState = transfer(i, inState)
            if (!lattice.lessThanOrEqual(outState, state[i])) {
                state[i] = lattice.join(state[i], outState)
                changed = true
            }
        }
    } while (changed)
    return state
}

enum class Direction { FORWARD, BACKWARD }

// Example: Constant propagation transfer function generator
sealed class ConstVec {
    object Top : ConstVec()
    object Bottom : ConstVec()
    data class Const(val values: List<Double>) : ConstVec()
}

object ConstVecLattice : Lattice<ConstVec> {
    override fun bottom() = ConstVec.Bottom
    override fun join(a: ConstVec, b: ConstVec): ConstVec = when {
        a is ConstVec.Bottom -> b
        b is ConstVec.Bottom -> a
        a is ConstVec.Top || b is ConstVec.Top -> ConstVec.Top
        a is ConstVec.Const && b is ConstVec.Const ->
            if (a.values == b.values) a else ConstVec.Top
        else -> ConstVec.Top
    }
    override fun lessThanOrEqual(a: ConstVec, b: ConstVec): Boolean = when {
        a is ConstVec.Bottom -> true
        b is ConstVec.Top -> true
        a is ConstVec.Const && b is ConstVec.Const -> a.values == b.values
        else -> false
    }
}

// --- Constant Propagation using new dataflow framework ---

fun evalConstExpr(expr: Expr, env: Map<String, ConstVec>): ConstVec = when (expr) {
    is NumberLiteral -> ConstVec.Const(listOf(expr.value))
    is IdRef -> env[expr.name] ?: ConstVec.Top
    is TensorLiteral -> if (expr.elements.all { evalConstExpr(it, env) is ConstVec.Const })
        ConstVec.Const(expr.elements.map { (evalConstExpr(it, env) as ConstVec.Const).values.single() })
    else ConstVec.Top
    is BinaryOp -> {
        val l = evalConstExpr(expr.left, env)
        val r = evalConstExpr(expr.right, env)
        if (l is ConstVec.Const && r is ConstVec.Const) {
            val lv = l.values
            val rv = r.values
            when (expr.op) {
                Op.Plus -> ConstVec.Const(lv.zip(rv) { a, b -> a + b })
                Op.Minus -> ConstVec.Const(lv.zip(rv) { a, b -> a - b })
                Op.Times -> ConstVec.Const(lv.zip(rv) { a, b -> a * b })
                Op.Div -> ConstVec.Const(lv.zip(rv) { a, b -> a / b })
                Op.TensorProd -> ConstVec.Const(lv.zip(rv) { a, b -> a * b })
                else -> ConstVec.Top
            }
        } else ConstVec.Top
    }
    is UnaryOp -> {
        val v = evalConstExpr(expr.expr, env)
        when (expr.op) {
            Op.Minus -> if (v is ConstVec.Const) ConstVec.Const(v.values.map { -it }) else ConstVec.Top
            Op.Transpose, Op.Length, Op.Dim -> ConstVec.Top
            else -> ConstVec.Top
        }
    }
    is ParenExpr -> evalConstExpr(expr.expr, env)
    is IndexOp -> {
        val base = evalConstExpr(expr.expr, env)
        val idx = evalConstExpr(expr.index, env)
        if (base is ConstVec.Const && idx is ConstVec.Const && idx.values.size == 1) {
            val i = idx.values[0].toInt()
            if (i in base.values.indices) ConstVec.Const(listOf(base.values[i])) else ConstVec.Top
        } else ConstVec.Top
    }
}

fun Program.constantPropagationEnv(): Map<String, ConstVec> {
    // Assume a trivial CFG: one node per statement, linear flow
    val cfg = CFG(statements.indices.map { idx ->
        CFG.CFGNode(idx, if (idx == 0) listOf() else listOf(idx - 1), if (idx == statements.size - 1) listOf() else listOf(idx + 1))
    })
    // Dataflow state: Map<String, ConstVec> per statement
    val lattice = object : Lattice<Map<String, ConstVec>> {
        override fun bottom() = emptyMap<String, ConstVec>()
        override fun join(a: Map<String, ConstVec>, b: Map<String, ConstVec>): Map<String, ConstVec> =
            (a.keys + b.keys).associateWith { k ->
                ConstVecLattice.join(a[k] ?: ConstVec.Bottom, b[k] ?: ConstVec.Bottom)
            }
        override fun lessThanOrEqual(a: Map<String, ConstVec>, b: Map<String, ConstVec>): Boolean =
            (a.keys + b.keys).all { k -> ConstVecLattice.lessThanOrEqual(a[k] ?: ConstVec.Bottom, b[k] ?: ConstVec.Bottom) }
    }
    val transfer = { idx: Int, inEnv: Map<String, ConstVec> ->
        val stmt = statements[idx]
        val outEnv = inEnv.toMutableMap()
        when (stmt) {
            is Assignment -> {
                val v = evalConstExpr(stmt.expr, inEnv)
                outEnv[stmt.id] = v
            }
            is PrintStmt -> {
                evalConstExpr(stmt.expr, inEnv)
            }
            // Extend for IfStmt, WhileStmt as needed
            else -> {}
        }
        outEnv
    }
    val states = runDataflow(cfg, lattice, transfer)
    // Return the final environment after the last statement
    return if (states.isNotEmpty()) states.last() else emptyMap()
}


fun Program.rewriteWithConstants(env: Map<String, ConstVec>): Program =
    Program(statements.map { it.rewriteWithConstants(env) })

fun Program.refineWithConstantPropagation(): Program {
    val env = this.constantPropagationEnv()
    return this.rewriteWithConstants(env)
}
// Minimal definition for LivenessInfo
data class LivenessInfo(val liveVars: Set<String>)

fun Statement.rewriteWithConstants(env: Map<String, ConstVec>): Statement = when (this) {
    is Assignment -> Assignment(id, expr.rewriteWithConstants(env))
    is PrintStmt -> PrintStmt(expr.rewriteWithConstants(env))
    is IfStmt -> IfStmt(cond, thenBranch.map { it.rewriteWithConstants(env) }, elseBranch?.map { it.rewriteWithConstants(env) })
    is WhileStmt -> WhileStmt(cond, body.map { it.rewriteWithConstants(env) })
}

fun Expr.rewriteWithConstants(env: Map<String, ConstVec>): Expr = when (this) {
    is NumberLiteral, is TensorLiteral -> this
    is IdRef -> {
        when (val v = env[this.name]) {
            is ConstVec.Const if v.values.size == 1 -> NumberLiteral(v.values[0])
            is ConstVec.Const -> TensorLiteral(v.values.map { NumberLiteral(it) })
            else -> this
        }
    }
    is BinaryOp -> {
        val leftR = left.rewriteWithConstants(env)
        val rightR = right.rewriteWithConstants(env)
        val lVal = (leftR as? NumberLiteral)?.value
        val rVal = (rightR as? NumberLiteral)?.value
        if (lVal != null && rVal != null) NumberLiteral(
            when (op) {
                Op.Plus -> lVal + rVal
                Op.Minus -> lVal - rVal
                Op.Times -> lVal * rVal
                Op.Div -> lVal / rVal
                Op.TensorProd -> lVal * rVal
                else -> lVal
            }
        ) else BinaryOp(leftR, op, rightR)
    }
    is UnaryOp -> {
        val exprR = expr.rewriteWithConstants(env)
        val v = (exprR as? NumberLiteral)?.value
        when (op) {
            Op.Minus -> if (v != null) NumberLiteral(-v) else UnaryOp(op, exprR)
            Op.Transpose, Op.Length, Op.Dim -> UnaryOp(op, exprR)
            else -> UnaryOp(op, exprR)
        }
    }
    is ParenExpr -> ParenExpr(expr.rewriteWithConstants(env))
    is IndexOp -> IndexOp(this.expr.rewriteWithConstants(env), this.index.rewriteWithConstants(env))
}

fun Program.livenessAnalysis(): List<LivenessInfo> {
    // Backward analysis: for each statement, compute live variables after
    val n = statements.size
    val liveAfter = Array(n + 1) { emptySet<String>() }
    for (i in n - 1 downTo 0) {
        val stmt = statements[i]
        val used = stmt.usedVars()
        val defined = stmt.definedVars()
        liveAfter[i] = (liveAfter[i + 1] - defined) + used
    }
    return (0 until n).map { LivenessInfo(liveAfter[it + 1]) }
}

fun Statement.usedVars(): Set<String> = when (this) {
    is Assignment -> expr.usedVars()
    is PrintStmt -> expr.usedVars()
    is IfStmt -> cond.usedVars().union(thenBranch.flatMap { it.usedVars() }).union(elseBranch?.flatMap { it.usedVars() } ?: emptyList())
    is WhileStmt -> cond.usedVars().union(body.flatMap { it.usedVars() })
}

fun Condition.usedVars(): Set<String> = left.usedVars().union(right.usedVars())

fun Statement.definedVars(): Set<String> = when (this) {
    is Assignment -> setOf(id)
    is PrintStmt -> emptySet()
    is IfStmt -> (thenBranch.flatMap { it.definedVars() } + (elseBranch?.flatMap { it.definedVars() } ?: emptyList())).toSet()
    is WhileStmt -> body.flatMap { it.definedVars() }.toSet()
}

fun Expr.usedVars(): Set<String> = when (this) {
    is NumberLiteral -> emptySet()
    is IdRef -> setOf(name)
    is TensorLiteral -> elements.flatMap { it.usedVars() }.toSet()
    is BinaryOp -> left.usedVars().union(right.usedVars())
    is UnaryOp -> expr.usedVars()
    is ParenExpr -> expr.usedVars()
    is IndexOp -> this.expr.usedVars().union((this as IndexOp).index.usedVars())
}

fun CFG.dumpToDot(filename: String, stmts: List<Statement>? = null) {
    val sb = StringBuilder()
    sb.appendLine("digraph CFG {")
    for (node in nodes) {
        val label = if (stmts != null && node.index < stmts.size) {
            val stmtStr = prettyPrintAst(stmts[node.index]).replace("\"", "\\\"")
            "${node.index}: $stmtStr"
        } else {
            node.index.toString()
        }
        sb.appendLine("  ${node.index} [label=\"$label\"];")
        for (succ in node.succs) {
            sb.appendLine("  ${node.index} -> $succ;")
        }
    }
    sb.appendLine("}")
    java.io.File(filename).writeText(sb.toString())
}

fun Program.dumpCfgToDot(filename: String) {
    // Build a trivial linear CFG: one node per statement
    val cfg = CFG(statements.indices.map { idx ->
        CFG.CFGNode(idx, if (idx == 0) listOf() else listOf(idx - 1), if (idx == statements.size - 1) listOf() else listOf(idx + 1))
    })
    cfg.dumpToDot(filename, statements)
}
