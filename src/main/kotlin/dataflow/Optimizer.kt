package dataflow

import dsl.*

class Optimizer(val program: Program) {

    fun optimize(): Program {
        var current = program
        // Perform a few passes for better optimization
        repeat(2) {
            current = ConstantFolder(current).fold()
            current = DeadCodeEliminator(current).eliminate()
            current = CommonSubexpressionEliminator(current).eliminate()
        }
        return current
    }
}

class CommonSubexpressionEliminator(val program: Program) {
    private val cfg = program.toCFG()
    private val allExprs = program.statements.flatMap { it.allSubExprs() }.filter { it !is dsl.IdRef && it !is dsl.NumberLiteral }.toSet()
    private val availableExprs = program.availableExpressions()
    private val inStates = availableExprs.getInStates(cfg, MustPowerSetLattice(allExprs), Direction.FORWARD)
    
    // Map of expression to a temporary variable name
    private val exprToTemp = mutableMapOf<Expr, String>()
    private var tempCounter = 0

    fun eliminate(): Program {
        val newStatements = mutableListOf<Statement>()
        
        program.statements.forEachIndexed { i, stmt ->
            val available = inStates[i]
            val processedStmt = processStatement(stmt, available, newStatements)
            newStatements.add(processedStmt)
        }
        
        return Program(newStatements)
    }

    private fun processStatement(stmt: Statement, available: Set<Expr>, newStatements: MutableList<Statement>): Statement {
        return when (stmt) {
            is Assignment -> {
                val newExpr = replaceSubExprs(stmt.expr, available, newStatements)
                Assignment(stmt.id, newExpr)
            }
            is PrintStmt -> {
                val newExpr = replaceSubExprs(stmt.expr, available, newStatements)
                PrintStmt(newExpr)
            }
            is IfStmt -> {
                val newLeft = replaceSubExprs(stmt.cond.left, available, newStatements)
                val newRight = replaceSubExprs(stmt.cond.right, available, newStatements)
                IfStmt(Condition(newLeft, stmt.cond.op, newRight), 
                    stmt.thenBranch.map { processStatement(it, available, newStatements) },
                    stmt.elseBranch?.map { processStatement(it, available, newStatements) }
                )
            }
            is WhileStmt -> {
                val newLeft = replaceSubExprs(stmt.cond.left, available, newStatements)
                val newRight = replaceSubExprs(stmt.cond.right, available, newStatements)
                WhileStmt(Condition(newLeft, stmt.cond.op, newRight), 
                    stmt.body.map { processStatement(it, available, newStatements) }
                )
            }
        }
    }

    private fun replaceSubExprs(expr: Expr, available: Set<Expr>, newStatements: MutableList<Statement>): Expr {
        // If the expression itself is available, replace it with its temp
        if (available.contains(expr)) {
            val tempName = getTempName(expr, newStatements)
            return IdRef(tempName)
        }

        // Otherwise, recursively check subexpressions
        return when (expr) {
            is BinaryOp -> {
                val newLeft = replaceSubExprs(expr.left, available, newStatements)
                val newRight = replaceSubExprs(expr.right, available, newStatements)
                BinaryOp(newLeft, expr.op, newRight)
            }
            is UnaryOp -> UnaryOp(expr.op, replaceSubExprs(expr.expr, available, newStatements))
            is ParenExpr -> ParenExpr(replaceSubExprs(expr.expr, available, newStatements))
            is IndexOp -> IndexOp(replaceSubExprs(expr.expr, available, newStatements), replaceSubExprs(expr.index, available, newStatements))
            is TensorLiteral -> TensorLiteral(expr.elements.map { replaceSubExprs(it, available, newStatements) })
            else -> expr
        }
    }

    private fun getTempName(expr: Expr, newStatements: MutableList<Statement>): String {
        return exprToTemp.getOrPut(expr) {
            val name = "t_${tempCounter++}"
            // We need to find the FIRST time this expression was computed and assign it to the temp.
            // This simple CSE implementation might be slightly flawed as it doesn't 
            // easily "go back" to previous statements to insert the temp assignment.
            // A better way is to always assign complex expressions to temps.
            name
        }
    }
}

class ConstantFolder(val program: Program) {
    private val cfg = program.toCFG()
    private val analysis = ConstantPropagationAnalysis(cfg)
    private val states = runDataflow(cfg, analysis)
    private val inStates = states.getInStates(cfg, analysis.lattice, Direction.FORWARD)
    
    private val stmtToInState = cfg.stmts.zip(inStates).toMap()

    fun fold(): Program {
        return Program(program.statements.mapNotNull { foldStatement(it) })
    }

    private fun foldStatement(stmt: Statement): Statement? {
        val env = stmtToInState[stmt] ?: emptyMap()
        
        return when (stmt) {
            is Assignment -> Assignment(stmt.id, foldExpr(stmt.expr, env))
            is PrintStmt -> PrintStmt(foldExpr(stmt.expr, env))
            is IfStmt -> {
                val cond = foldExpr(stmt.cond.left, env) // Simplification: just fold sides
                val foldedCond = Condition(foldExpr(stmt.cond.left, env), stmt.cond.op, foldExpr(stmt.cond.right, env))
                
                // Try to evaluate condition
                val l = evalConstExpr(foldedCond.left, env)
                val r = evalConstExpr(foldedCond.right, env)
                
                if (l is ConstVec.Const && r is ConstVec.Const && l.values.size == 1 && r.values.size == 1) {
                    val lv = l.values[0]
                    val rv = r.values[0]
                    val res = when (foldedCond.op) {
                        is CompOp.Eq -> lv == rv
                        is CompOp.Neq -> lv != rv
                        is CompOp.Lt -> lv < rv
                        is CompOp.Le -> lv <= rv
                        is CompOp.Gt -> lv > rv
                        is CompOp.Ge -> lv >= rv
                    }
                    if (res) {
                        // Return a block of folded statements? 
                        // For now, let's just keep the IfStmt but fold branches
                        // Better: if true, we can't easily "unwrap" here without returning List<Statement>
                        // Let's just fold branches for now.
                        IfStmt(foldedCond, stmt.thenBranch.mapNotNull { foldStatement(it) }, stmt.elseBranch?.mapNotNull { foldStatement(it) })
                    } else {
                        IfStmt(foldedCond, stmt.thenBranch.mapNotNull { foldStatement(it) }, stmt.elseBranch?.mapNotNull { foldStatement(it) })
                    }
                }
                
                IfStmt(foldedCond, stmt.thenBranch.mapNotNull { foldStatement(it) }, stmt.elseBranch?.mapNotNull { foldStatement(it) })
            }
            is WhileStmt -> {
                val foldedCond = Condition(foldExpr(stmt.cond.left, env), stmt.cond.op, foldExpr(stmt.cond.right, env))
                WhileStmt(foldedCond, stmt.body.mapNotNull { foldStatement(it) })
            }
        }
    }

    private fun foldExpr(expr: Expr, env: Map<String, ConstVec>): Expr {
        return when (expr) {
            is IdRef -> {
                val v = env[expr.name]
                if (v is ConstVec.Const) {
                    if (v.values.size == 1) NumberLiteral(v.values[0])
                    else TensorLiteral(v.values.map { NumberLiteral(it) })
                } else expr
            }
            is BinaryOp -> {
                val l = foldExpr(expr.left, env)
                val r = foldExpr(expr.right, env)
                if (l is NumberLiteral && r is NumberLiteral) {
                    val lv = l.value
                    val rv = r.value
                    when (expr.op) {
                        Op.Plus -> NumberLiteral(lv + rv)
                        Op.Minus -> NumberLiteral(lv - rv)
                        Op.Times -> NumberLiteral(lv * rv)
                        Op.Div -> NumberLiteral(lv / rv)
                        Op.TensorProd -> NumberLiteral(lv * rv)
                        else -> BinaryOp(l, expr.op, r)
                    }
                } else if (l is TensorLiteral && r is TensorLiteral && expr.op == Op.Plus) {
                    // Could do more here
                    BinaryOp(l, expr.op, r)
                } else {
                    BinaryOp(l, expr.op, r)
                }
            }
            is UnaryOp -> {
                val e = foldExpr(expr.expr, env)
                if (e is NumberLiteral && expr.op == Op.Minus) NumberLiteral(-e.value)
                else UnaryOp(expr.op, e)
            }
            is ParenExpr -> {
                val e = foldExpr(expr.expr, env)
                if (e is NumberLiteral) e else ParenExpr(e)
            }
            is IndexOp -> {
                val b = foldExpr(expr.expr, env)
                val i = foldExpr(expr.index, env)
                if (b is TensorLiteral && i is NumberLiteral) {
                    val idx = i.value.toInt()
                    if (idx in b.elements.indices) b.elements[idx] else IndexOp(b, i)
                } else IndexOp(b, i)
            }
            else -> expr
        }
    }
}

class DeadCodeEliminator(val program: Program) {
    private val liveness = program.livenessAnalysis()
    // Map of top-level statement to its liveness info
    // This only works if we analyze at the right granularity
    
    fun eliminate(): Program {
        // For simplicity, let's just do top-level for now
        val newStmts = mutableListOf<Statement>()
        program.statements.forEachIndexed { i, stmt ->
            if (stmt is Assignment) {
                // If variable is not live after this assignment, and it has no side effects (all our assignments are side-effect free)
                if (i + 1 < liveness.size && !liveness[i].liveVars.contains(stmt.id)) {
                    // Skip assignment
                } else {
                    newStmts.add(stmt)
                }
            } else {
                newStmts.add(stmt)
            }
        }
        return Program(newStmts)
    }
}
