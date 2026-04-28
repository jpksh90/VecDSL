// Basic block representation for CFG
data class BasicBlock(val index: Int, val statements: List<Statement>, val preds: MutableList<Int> = mutableListOf(), val succs: MutableList<Int> = mutableListOf())

// Build basic blocks from a flat list of statements (trivial version: one block for all statements)
fun Program.toBasicBlocks(): List<BasicBlock> {
    // For now, treat the whole program as a single block (extend for real branches/labels)
    return listOf(BasicBlock(0, statements))
}

// Build a block-level CFG (trivial version: single block, no edges)
fun Program.toBlockCFG(): CFG {
    val blocks = toBasicBlocks()
    val nodes = blocks.map { block ->
        CFG.CFGNode(block.index, block.preds, block.succs)
    }
    return CFG(nodes)
}

// Control Flow Graph (CFG) representation
class CFG(val nodes: List<CFGNode>) {
    data class CFGNode(val index: Int, val preds: List<Int>, val succs: List<Int>)
}

fun Program.toCFG(): CFG {
    val n = statements.size
    val nodes = List(n) { idx ->
        val preds = if (idx == 0) emptyList() else listOf(idx - 1)
        val succs = if (idx == n - 1) emptyList() else listOf(idx + 1)
        CFG.CFGNode(idx, preds, succs)
    }
    return CFG(nodes)
}

// Abstract Syntax Tree (AST) definitions for TensorDSL

sealed class TensorAstNode

class Program(val statements: List<Statement>) : TensorAstNode()

sealed class Statement : TensorAstNode()
class Assignment(val id: String, val expr: Expr) : Statement()
class PrintStmt(val expr: Expr) : Statement()
class IfStmt(val cond: Condition, val thenBranch: List<Statement>, val elseBranch: List<Statement>?) : Statement()
class WhileStmt(val cond: Condition, val body: List<Statement>) : Statement()

class Condition(val left: Expr, val op: CompOp, val right: Expr) : TensorAstNode()
sealed class CompOp : TensorAstNode() {
    object Eq : CompOp()
    object Neq : CompOp()
    object Lt : CompOp()
    object Le : CompOp()
    object Gt : CompOp()
    object Ge : CompOp()
}

sealed class Expr : TensorAstNode()
class NumberLiteral(val value: Double) : Expr()
class IdRef(val name: String) : Expr()
class TensorLiteral(val elements: List<Expr>) : Expr()
class BinaryOp(val left: Expr, val op: Op, val right: Expr) : Expr()
class UnaryOp(val op: Op, val expr: Expr) : Expr()
class ParenExpr(val expr: Expr) : Expr()

sealed class Op {
    object Plus : Op()
    object Minus : Op()
    object Times : Op()
    object Div : Op()
    object TensorProd : Op()
}

// Abstract interpreter framework for TensorDSL AST
interface Lattice<T> {
    fun bottom(): T
    fun join(a: T, b: T): T
    fun lessThanOrEqual(a: T, b: T): Boolean
}

abstract class AbstractInterpreter<T>(private val lattice: Lattice<T>) {
    abstract fun evalNumber(value: Double): T
    abstract fun evalTensor(elements: List<T>): T
    abstract fun evalOp(op: Op, left: T, right: T): T
    abstract fun evalUnary(op: Op, value: T): T
    abstract fun evalId(name: String, env: Map<String, T>): T

    fun interpret(program: Program, env: Map<String, T> = emptyMap()): Map<String, T> {
        var state = env.toMutableMap()
        for (stmt in program.statements) {
            when (stmt) {
                is Assignment -> {
                    val v = evalExpr(stmt.expr, state)
                    state[stmt.id] = v
                }
                is PrintStmt -> {
                    // No effect on state
                    evalExpr(stmt.expr, state)
                }
                is IfStmt -> TODO("IfStmt not implemented")
                is WhileStmt -> TODO("WhileStmt not implemented")
            }
        }
        return state
    }

    fun evalExpr(expr: Expr, env: Map<String, T>): T = when (expr) {
        is NumberLiteral -> evalNumber(expr.value)
        is IdRef -> evalId(expr.name, env)
        is TensorLiteral -> evalTensor(expr.elements.map { evalExpr(it, env) })
        is BinaryOp -> evalOp(expr.op, evalExpr(expr.left, env), evalExpr(expr.right, env))
        is UnaryOp -> evalUnary(expr.op, evalExpr(expr.expr, env))
        is ParenExpr -> evalExpr(expr.expr, env)
    }
}

// Constant propagation domain for vectors
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

        else -> {ConstVec.Top}
    }
    override fun lessThanOrEqual(a: ConstVec, b: ConstVec): Boolean = when {
        a is ConstVec.Bottom -> true
        b is ConstVec.Top -> true
        a is ConstVec.Const && b is ConstVec.Const -> a.values == b.values
        else -> false
    }
}

class ConstVecInterpreter : AbstractInterpreter<ConstVec>(ConstVecLattice) {
    override fun evalNumber(value: Double): ConstVec = ConstVec.Const(listOf(value))
    override fun evalTensor(elements: List<ConstVec>): ConstVec =
        if (elements.all { it is ConstVec.Const })
            ConstVec.Const(elements.map { (it as ConstVec.Const).values.single() })
        else ConstVec.Top
    override fun evalOp(op: Op, left: ConstVec, right: ConstVec): ConstVec = when {
        left is ConstVec.Const && right is ConstVec.Const -> {
            val l = left.values
            val r = right.values
            when (op) {
                Op.Plus -> ConstVec.Const(l.zip(r) { a, b -> a + b })
                Op.Minus -> ConstVec.Const(l.zip(r) { a, b -> a - b })
                Op.Times -> ConstVec.Const(l.zip(r) { a, b -> a * b })
                Op.Div -> ConstVec.Const(l.zip(r) { a, b -> a / b })
                Op.TensorProd -> ConstVec.Const(l.zip(r) { a, b -> a * b })
            }
        }
        else -> ConstVec.Top
    }
    override fun evalUnary(op: Op, value: ConstVec): ConstVec = when {
        value is ConstVec.Const -> when (op) {
            Op.Minus -> ConstVec.Const(value.values.map { -it })
            else -> ConstVec.Top
        }
        else -> ConstVec.Top
    }
    override fun evalId(name: String, env: Map<String, ConstVec>): ConstVec = env[name] ?: ConstVec.Top
}

fun Program.rewriteWithConstants(env: Map<String, ConstVec>): Program =
    Program(statements.map { it.rewriteWithConstants(env) })

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
        if (lVal != null && rVal != null) NumberLiteral(ConstVecInterpreter().evalOp(op, ConstVec.Const(listOf(lVal)), ConstVec.Const(listOf(rVal))).let { (it as? ConstVec.Const)?.values?.first() ?: lVal })
        else BinaryOp(leftR, op, rightR)
    }
    is UnaryOp -> {
        val exprR = expr.rewriteWithConstants(env)
        val v = (exprR as? NumberLiteral)?.value
        if (v != null) NumberLiteral(ConstVecInterpreter().evalUnary(op, ConstVec.Const(listOf(v))).let { (it as? ConstVec.Const)?.values?.first() ?: v })
        else UnaryOp(op, exprR)
    }
    is ParenExpr -> ParenExpr(expr.rewriteWithConstants(env))
}

fun Program.refineWithConstantPropagation(): Program {
    val env = ConstVecInterpreter().interpret(this)
    return this.rewriteWithConstants(env)
}

// AST pipeline framework (functional style)
typealias AstTransformer = (Program) -> Program

fun constantPropagationTransformer(): AstTransformer = { it.refineWithConstantPropagation() }

// Abstract interpreter for double transpose elimination
fun Expr.isTranspose(): Boolean = this is UnaryOp && this.op == Op.TensorProd
fun Expr.unwrapTranspose(): Expr = if (this.isTranspose()) (this as UnaryOp).expr else this

fun Expr.eliminateDoubleTranspose(): Expr = when (this) {
    is UnaryOp -> {
        if (op == Op.TensorProd && expr.isTranspose()) {
            // Double transpose: check if inner is a vector and not modified
            val inner = expr.unwrapTranspose()
            // Only eliminate if inner is a vector literal or variable (not an expression)
            when (inner) {
                is IdRef, is TensorLiteral -> inner.eliminateDoubleTranspose()
                else -> UnaryOp(op, expr.eliminateDoubleTranspose())
            }
        } else UnaryOp(op, expr.eliminateDoubleTranspose())
    }
    is BinaryOp -> BinaryOp(left.eliminateDoubleTranspose(), op, right.eliminateDoubleTranspose())
    is ParenExpr -> ParenExpr(expr.eliminateDoubleTranspose())
    is TensorLiteral -> TensorLiteral(elements.map { it.eliminateDoubleTranspose() })
    else -> this
}

fun Statement.eliminateDoubleTranspose(): Statement = when (this) {
    is Assignment -> Assignment(id, expr.eliminateDoubleTranspose())
    is PrintStmt -> PrintStmt(expr.eliminateDoubleTranspose())
    is IfStmt -> IfStmt(cond, thenBranch.map { it.eliminateDoubleTranspose() }, elseBranch?.map { it.eliminateDoubleTranspose() })
    is WhileStmt -> WhileStmt(cond, body.map { it.eliminateDoubleTranspose() })
}

fun Program.eliminateDoubleTranspose(): Program =
    Program(statements.map { it.eliminateDoubleTranspose() })

fun doubleTransposeEliminationTransformer(): AstTransformer = { it.eliminateDoubleTranspose() }

fun astPipeline(vararg transformers: AstTransformer): AstTransformer = { program ->
    transformers.fold(program) { acc, transformer -> transformer(acc) }
}

// Liveness and Reaching Definitions Analysis

data class LivenessInfo(val liveVars: Set<String>)
data class ReachingDefInfo(val reachingDefs: Map<String, Set<Int>>)

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
}



fun Program.applyDefaultPipeline(): Program {
    val pipeline = astPipeline(
        constantPropagationTransformer(),
        doubleTransposeEliminationTransformer(),
    )
    return pipeline(this)
}
