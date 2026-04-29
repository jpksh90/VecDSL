package dataflow

import dsl.Expr
import dsl.IdRef
import prettyPrintAst
import java.util.*

interface Lattice<T> {
    fun bottom(): T
    fun join(a: T, b: T): T
    fun lessThanOrEqual(a: T, b: T): Boolean
}

enum class Direction { FORWARD, BACKWARD }

interface Analysis<T> {
    val direction: Direction
    val lattice: Lattice<T>
    fun transfer(stmtIdx: Int, input: T): T
    fun initialState(cfg: CFG): List<T> = List(cfg.nodes.size) { lattice.bottom() }
}

// Generic dataflow analysis framework using a worklist solver
fun <T> runDataflow(cfg: CFG, analysis: Analysis<T>): List<T> {
    val n = cfg.nodes.size
    val lattice = analysis.lattice
    
    val state = analysis.initialState(cfg).toMutableList()
    
    val worklist: Queue<Int> = LinkedList<Int>()
    for (i in 0 until n) {
        worklist.add(i)
    }

    while (worklist.isNotEmpty()) {
        val i = worklist.poll()
        val node = cfg.nodes[i]
        
        val neighbors = if (analysis.direction == Direction.FORWARD) node.preds else node.succs
        val input = if (neighbors.isEmpty()) {
            lattice.bottom() 
        } else {
            var res = state[neighbors[0]]
            for (j in 1 until neighbors.size) {
                res = lattice.join(res, state[neighbors[j]])
            }
            res
        }
        
        val output = analysis.transfer(i, input)
        
        if (!lattice.lessThanOrEqual(output, state[i])) {
            state[i] = output
            val toAdd = if (analysis.direction == Direction.FORWARD) node.succs else node.preds
            for (next in toAdd) {
                if (!worklist.contains(next)) {
                    worklist.add(next)
                }
            }
        }
    }
    return state
}

// Helper to get state BEFORE statement in forward analysis
fun <T> List<T>.getInStates(cfg: CFG, lattice: Lattice<T>, direction: Direction): List<T> {
    return List(this.size) { i ->
        val node = cfg.nodes[i]
        val neighbors = if (direction == Direction.FORWARD) node.preds else node.succs
        if (neighbors.isEmpty()) {
            lattice.bottom()
        } else {
            var res = this[neighbors[0]]
            for (j in 1 until neighbors.size) {
                res = lattice.join(res, this[neighbors[j]])
            }
            res
        }
    }
}

// --- Gen-Kill Framework ---

abstract class GenKillAnalysis<T>(val cfg: CFG) : Analysis<Set<T>> {
    abstract val gen: List<Set<T>>
    abstract val kill: List<Set<T>>
    
    override fun transfer(stmtIdx: Int, input: Set<T>): Set<T> {
        return (input - kill[stmtIdx]) + gen[stmtIdx]
    }
}

// --- Lattices ---

data class PowerSetLattice<T>(val universe: Set<T>) : Lattice<Set<T>> {
    override fun bottom(): Set<T> = emptySet()
    override fun join(a: Set<T>, b: Set<T>): Set<T> = a union b
    override fun lessThanOrEqual(a: Set<T>, b: Set<T>): Boolean = b.containsAll(a)
}

data class MustPowerSetLattice<T>(val universe: Set<T>) : Lattice<Set<T>> {
    override fun bottom(): Set<T> = universe
    override fun join(a: Set<T>, b: Set<T>): Set<T> = a intersect b
    override fun lessThanOrEqual(a: Set<T>, b: Set<T>): Boolean = a.containsAll(b)
}

// --- Constant Propagation ---

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

fun evalConstExpr(expr: dsl.Expr, env: Map<String, ConstVec>): ConstVec = when (expr) {
    is dsl.NumberLiteral -> ConstVec.Const(listOf(expr.value))
    is dsl.IdRef -> env[expr.name] ?: ConstVec.Top
    is dsl.TensorLiteral -> {
        val results = expr.elements.map { evalConstExpr(it, env) }
        if (results.all { it is ConstVec.Const })
            ConstVec.Const(results.flatMap { (it as ConstVec.Const).values })
        else ConstVec.Top
    }
    is dsl.BinaryOp -> {
        val l = evalConstExpr(expr.left, env)
        val r = evalConstExpr(expr.right, env)
        if (l is ConstVec.Const && r is ConstVec.Const) {
            val lv = l.values
            val rv = r.values
            if (lv.size != rv.size && lv.size != 1 && rv.size != 1) ConstVec.Top
            else {
                when (expr.op) {
                    dsl.Op.Plus -> ConstVec.Const(lv.zip(rv) { a, b -> a + b })
                    dsl.Op.Minus -> ConstVec.Const(lv.zip(rv) { a, b -> a - b })
                    dsl.Op.Times -> ConstVec.Const(lv.zip(rv) { a, b -> a * b })
                    dsl.Op.Div -> ConstVec.Const(lv.zip(rv) { a, b -> a / b })
                    dsl.Op.TensorProd -> ConstVec.Const(lv.flatMap { lVal -> rv.map { rVal -> lVal * rVal } })
                    else -> ConstVec.Top
                }
            }
        } else ConstVec.Top
    }
    is dsl.UnaryOp -> {
        val v = evalConstExpr(expr.expr, env)
        when (expr.op) {
            dsl.Op.Minus -> if (v is ConstVec.Const) ConstVec.Const(v.values.map { -it }) else ConstVec.Top
            dsl.Op.Transpose, dsl.Op.Length, dsl.Op.Dim -> ConstVec.Top
            else -> ConstVec.Top
        }
    }
    is dsl.ParenExpr -> evalConstExpr(expr.expr, env)
    is dsl.IndexOp -> {
        val base = evalConstExpr(expr.expr, env)
        val idx = evalConstExpr(expr.index, env)
        if (base is ConstVec.Const && idx is ConstVec.Const && idx.values.size == 1) {
            val i = idx.values[0].toInt()
            if (i in base.values.indices) ConstVec.Const(listOf(base.values[i])) else ConstVec.Top
        } else ConstVec.Top
    }
}

class ConstantPropagationAnalysis(val cfg: CFG) : Analysis<Map<String, ConstVec>> {
    override val direction = Direction.FORWARD
    override val lattice = object : Lattice<Map<String, ConstVec>> {
        override fun bottom() = emptyMap<String, ConstVec>()
        override fun join(a: Map<String, ConstVec>, b: Map<String, ConstVec>): Map<String, ConstVec> =
            (a.keys + b.keys).associateWith { k ->
                ConstVecLattice.join(a[k] ?: ConstVec.Bottom, b[k] ?: ConstVec.Bottom)
            }
        override fun lessThanOrEqual(a: Map<String, ConstVec>, b: Map<String, ConstVec>): Boolean =
            (a.keys + b.keys).all { k -> ConstVecLattice.lessThanOrEqual(a[k] ?: ConstVec.Bottom, b[k] ?: ConstVec.Bottom) }
    }

    override fun transfer(stmtIdx: Int, input: Map<String, ConstVec>): Map<String, ConstVec> {
        val stmt = cfg.stmts[stmtIdx]
        val output = input.toMutableMap()
        when (stmt) {
            is dsl.Assignment -> {
                output[stmt.id] = evalConstExpr(stmt.expr, input)
            }
            is dsl.Phi -> {
                output[stmt.id] = ConstVec.Top
            }
            else -> {}
        }
        return output
    }
}

fun dsl.Program.constantPropagationEnv(): Map<String, ConstVec> {
    val cfg = this.toCFG()
    val analysis = ConstantPropagationAnalysis(cfg)
    val states = runDataflow(cfg, analysis)
    return if (states.isNotEmpty()) states.last() else emptyMap()
}

// --- Liveness Analysis ---

class LivenessAnalysis(cfg: CFG) : GenKillAnalysis<String>(cfg) {
    override val direction = Direction.BACKWARD
    override val lattice = PowerSetLattice<String>(emptySet())
    
    override val gen = cfg.stmts.map { it.usedVars() }
    override val kill = cfg.stmts.map { it.definedVars() }
}

data class LivenessInfo(val liveVars: Set<String>)

fun dsl.Program.livenessAnalysis(): List<LivenessInfo> {
    val cfg = this.toCFG()
    val analysis = LivenessAnalysis(cfg)
    val states = runDataflow(cfg, analysis)
    return states.map { LivenessInfo(it) }
}

// --- Available Expressions Analysis ---

class AvailableExpressionsAnalysis(cfg: CFG) : GenKillAnalysis<Expr>(cfg) {
    override val direction = Direction.FORWARD
    
    private val allExprs = cfg.stmts.flatMap { it.allSubExprs() }.filter { it !is IdRef && it !is dsl.NumberLiteral }.toSet()
    
    override val lattice = MustPowerSetLattice<Expr>(allExprs)
    
    override val gen = cfg.stmts.map { stmt ->
        val exprs = stmt.allSubExprs().filter { it !is IdRef && it !is dsl.NumberLiteral }.toSet()
        // An expression is generated if it's computed and its operands are not killed by the same statement
        val defined = stmt.definedVars()
        exprs.filter { e -> e.usedVars().intersect(defined).isEmpty() }.toSet()
    }
    
    override val kill = cfg.stmts.map { stmt ->
        val defined = stmt.definedVars()
        allExprs.filter { e -> e.usedVars().intersect(defined).isNotEmpty() }.toSet()
    }

    override fun initialState(cfg: CFG): List<Set<Expr>> {
        return List(cfg.nodes.size) { i -> if (i == 0) emptySet() else lattice.bottom() }
    }
}

fun dsl.Statement.allSubExprs(): List<Expr> = when (this) {
    is dsl.Assignment -> expr.allSubExprs() + expr
    is dsl.PrintStmt -> expr.allSubExprs() + expr
    is dsl.IfStmt -> cond.left.allSubExprs() + cond.left + cond.right.allSubExprs() + cond.right
    is dsl.WhileStmt -> cond.left.allSubExprs() + cond.left + cond.right.allSubExprs() + cond.right
    is dsl.Phi -> emptyList()
}

fun dsl.Expr.allSubExprs(): List<Expr> = when (this) {
    is dsl.BinaryOp -> left.allSubExprs() + left + right.allSubExprs() + right
    is dsl.UnaryOp -> expr.allSubExprs() + expr
    is dsl.ParenExpr -> expr.allSubExprs() + expr
    is dsl.IndexOp -> expr.allSubExprs() + expr + index.allSubExprs() + index
    is dsl.TensorLiteral -> elements.flatMap { it.allSubExprs() + it }
    else -> emptyList()
}

fun dsl.Program.availableExpressions(): List<Set<Expr>> {
    val cfg = this.toCFG()
    val analysis = AvailableExpressionsAnalysis(cfg)
    val states = runDataflow(cfg, analysis)
    return states
}

// --- Rewriting and Utilities ---

fun dsl.Program.refineWithConstantPropagation(): dsl.Program {
    val env = this.constantPropagationEnv()
    return this.rewriteWithConstants(env)
}

fun dsl.Program.rewriteWithConstants(env: Map<String, ConstVec>): dsl.Program =
    dsl.Program(statements.map { it.rewriteWithConstants(env) })

fun dsl.Statement.rewriteWithConstants(env: Map<String, ConstVec>): dsl.Statement = when (this) {
    is dsl.Assignment -> dsl.Assignment(id, expr.rewriteWithConstants(env))
    is dsl.PrintStmt -> dsl.PrintStmt(expr.rewriteWithConstants(env))
    is dsl.IfStmt -> dsl.IfStmt(
        cond,
        thenBranch.map { it.rewriteWithConstants(env) },
        elseBranch?.map { it.rewriteWithConstants(env) })
    is dsl.WhileStmt -> dsl.WhileStmt(cond, body.map { it.rewriteWithConstants(env) })
    is dsl.Phi -> this
}

fun dsl.Expr.rewriteWithConstants(env: Map<String, ConstVec>): dsl.Expr = when (this) {
    is dsl.NumberLiteral, is dsl.TensorLiteral -> this
    is dsl.IdRef -> {
        when (val v = env[this.name]) {
            is ConstVec.Const -> if (v.values.size == 1) dsl.NumberLiteral(v.values[0])
            else dsl.TensorLiteral(v.values.map { dsl.NumberLiteral(it) })
            else -> this
        }
    }
    is dsl.BinaryOp -> {
        val leftR = left.rewriteWithConstants(env)
        val rightR = right.rewriteWithConstants(env)
        val lVal = (leftR as? dsl.NumberLiteral)?.value
        val rVal = (rightR as? dsl.NumberLiteral)?.value
        if (lVal != null && rVal != null) dsl.NumberLiteral(
            when (op) {
                dsl.Op.Plus -> lVal + rVal
                dsl.Op.Minus -> lVal - rVal
                dsl.Op.Times -> lVal * rVal
                dsl.Op.Div -> lVal / rVal
                dsl.Op.TensorProd -> lVal * rVal
                else -> lVal
            }
        ) else dsl.BinaryOp(leftR, op, rightR)
    }
    is dsl.UnaryOp -> {
        val exprR = expr.rewriteWithConstants(env)
        val v = (exprR as? dsl.NumberLiteral)?.value
        when (op) {
            dsl.Op.Minus -> if (v != null) dsl.NumberLiteral(-v) else dsl.UnaryOp(op, exprR)
            else -> dsl.UnaryOp(op, exprR)
        }
    }
    is dsl.ParenExpr -> dsl.ParenExpr(expr.rewriteWithConstants(env))
    is dsl.IndexOp -> dsl.IndexOp(this.expr.rewriteWithConstants(env), this.index.rewriteWithConstants(env))
}

fun dsl.Statement.usedVars(): Set<String> = when (this) {
    is dsl.Assignment -> expr.usedVars()
    is dsl.PrintStmt -> expr.usedVars()
    is dsl.IfStmt -> cond.usedVars()
    is dsl.WhileStmt -> cond.usedVars()
    is dsl.Phi -> versions.values.toSet()
}

fun dsl.Condition.usedVars(): Set<String> = left.usedVars().union(right.usedVars())

fun dsl.Statement.definedVars(): Set<String> = when (this) {
    is dsl.Assignment -> setOf(id)
    is dsl.Phi -> setOf(id)
    else -> emptySet()
}

fun dsl.Expr.usedVars(): Set<String> = when (this) {
    is dsl.NumberLiteral -> emptySet()
    is dsl.IdRef -> setOf(name)
    is dsl.TensorLiteral -> elements.flatMap { it.usedVars() }.toSet()
    is dsl.BinaryOp -> left.usedVars().union(right.usedVars())
    is dsl.UnaryOp -> expr.usedVars()
    is dsl.ParenExpr -> expr.usedVars()
    is dsl.IndexOp -> this.expr.usedVars().union(this.index.usedVars())
}

fun dsl.Statement.toCfgLabel(): String = when (this) {
    is dsl.IfStmt -> "if (${prettyPrintAst(cond)})"
    is dsl.WhileStmt -> "while (${prettyPrintAst(cond)})"
    else -> prettyPrintAst(this).replace("\n", "\\n")
}

fun CFG.dumpToDot(filename: String) {
    val sb = StringBuilder()
    sb.appendLine("digraph CFG {")
    for (node in nodes) {
        val stmt = stmts[node.index]
        val stmtStr = stmt.toCfgLabel().replace("\"", "\\\"")
        val label = "${node.index}: $stmtStr"
        sb.appendLine("  ${node.index} [label=\"$label\"];")
        for (succ in node.succs) {
            val edgeLabel = node.edgeLabels[succ]
            val attr = if (edgeLabel != null) " [label=\"$edgeLabel\"]" else ""
            sb.appendLine("  ${node.index} -> $succ$attr;")
        }
    }
    sb.appendLine("}")
    java.io.File(filename).writeText(sb.toString())
}

fun BlockCFG.dumpToDot(filename: String) {
    val sb = StringBuilder()
    sb.appendLine("digraph BlockCFG {")
    sb.appendLine("  node [shape=box];")
    for (block in blocks) {
        val stmtsStr = block.statements.joinToString("\\n") { it.toCfgLabel().replace("\"", "\\\"") }
        val label = "Block ${block.index}\\n$stmtsStr"
        sb.appendLine("  ${block.index} [label=\"$label\"];")
        for (succ in block.succs) {
            val edgeLabel = block.edgeLabels[succ]
            val attr = if (edgeLabel != null) " [label=\"$edgeLabel\"]" else ""
            sb.appendLine("  ${block.index} -> $succ$attr;")
        }
    }
    sb.appendLine("}")
    java.io.File(filename).writeText(sb.toString())
}

fun dsl.Program.dumpCfgToDot(filename: String) {
    val cfg = this.toCFG()
    cfg.dumpToDot(filename)
}
