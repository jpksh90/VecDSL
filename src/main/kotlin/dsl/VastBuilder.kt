package dsl

import TensorDslParser
import codegen.generateExpr

// Builder logic only
object TensorAstBuilder {
    fun fromProgram(ctx: TensorDslParser.ProgramContext): Program? {
        return fromProgramChecked(ctx)
    }

    fun fromProgramChecked(ctx: TensorDslParser.ProgramContext): Program? {
        val ast = Program(ctx.statement().map { fromStatement(it) })
        return checkVariableDeclarations(ast)
    }

    private fun fromStatement(ctx: TensorDslParser.StatementContext): Statement = when {
        ctx.declaration() != null -> fromDeclaration(ctx.declaration())
        ctx.assignment() != null -> fromAssignment(ctx.assignment())
        ctx.printStmt() != null -> fromPrintStmt(ctx.printStmt())
        ctx.ifStmt() != null -> fromIfStmt(ctx.ifStmt())
        ctx.whileStmt() != null -> fromWhileStmt(ctx.whileStmt())
        else -> error("Unknown statement: ${ctx.text}")
    }

    private fun fromDeclaration(ctx: TensorDslParser.DeclarationContext): Declaration {
        val id = ctx.ID().text
        val expr = ctx.expr()?.let { fromExpr(it) }
        return Declaration(id, expr)
    }

    private fun fromAssignment(ctx: TensorDslParser.AssignmentContext): Assignment {
        val id = ctx.ID().text
        val exprCtx = ctx.expr() ?: error("Assignment to '$id' is missing an expression: ${ctx.text}")
        val expr = fromExpr(exprCtx)
        return Assignment(id, expr)
    }

    private fun fromPrintStmt(ctx: TensorDslParser.PrintStmtContext): PrintStmt {
        val expr = fromExpr(ctx.expr())
        return PrintStmt(expr)
    }

    private fun fromExpr(ctx: TensorDslParser.ExprContext): Expr = fromAddition(ctx.addition())

    private fun fromAddition(ctx: TensorDslParser.AdditionContext): Expr {
        var expr = fromTensorProduct(ctx.tensorProduct(0))
        for (i in 1 until ctx.tensorProduct().size) {
            val opToken = ctx.getChild(i * 2 - 1).text
            val op = when (opToken) {
                "+" -> Op.Plus
                "-" -> Op.Minus
                else -> error("Unknown add op: $opToken")
            }
            val right = fromTensorProduct(ctx.tensorProduct(i))
            expr = BinaryOp(expr, op, right)
        }
        return expr
    }

    private fun fromTensorProduct(ctx: TensorDslParser.TensorProductContext): Expr {
        var expr = fromMultiplication(ctx.multiplication(0))
        for (i in 1 until ctx.multiplication().size) {
            val op = when (val opToken = ctx.getChild(i * 2 - 1).text) {
                "#" -> Op.TensorProd
                else -> error("Unknown tensor product op: $opToken")
            }
            val right = fromMultiplication(ctx.multiplication(i))
            expr = BinaryOp(expr, op, right)
        }
        return expr
    }

    private fun fromMultiplication(ctx: TensorDslParser.MultiplicationContext): Expr {
        var expr = fromUnary(ctx.unary(0))
        for (i in 1 until ctx.unary().size) {
            val opToken = ctx.getChild(i * 2 - 1).text
            val op = when (opToken) {
                "*" -> Op.Times
                "/" -> Op.Div
                else -> error("Unknown mul op: $opToken")
            }
            val right = fromUnary(ctx.unary(i))
            expr = BinaryOp(expr, op, right)
        }
        return expr
    }

    private fun fromUnary(ctx: TensorDslParser.UnaryContext): Expr = when {
        ctx.getChild(0).text == "-" -> UnaryOp(Op.Minus, fromUnary(ctx.unary()))
        ctx.postfix() != null -> fromPostfix(ctx.postfix())
        else -> error("Unknown unary: ${ctx.text}")
    }

    private fun fromPostfix(ctx: TensorDslParser.PostfixContext): Expr {
        var expr = fromPrimary(ctx.primary())
        for (opCtx in ctx.postfixOp()) {
            expr = when (opCtx.text) {
                "tpos" -> UnaryOp(Op.Transpose, expr)
                "len" -> UnaryOp(Op.Length, expr)
                "dim" -> UnaryOp(Op.Dim, expr)
                else -> {
                    val idxLit = opCtx.indexLiteral() ?: error("Unknown postfix op: ${opCtx.text}")
                    if (idxLit.NUMBER() != null) {
                        IndexOp(expr, NumberLiteral(idxLit.NUMBER().text.toDouble()))
                    } else if (idxLit.ID() != null) {
                        IndexOp(expr, IdRef(idxLit.ID().text))
                    } else {
                        error("Unknown index literal: ${idxLit.text}")
                    }
                }
            }
        }
        return expr
    }

    private fun fromPrimary(ctx: TensorDslParser.PrimaryContext): Expr = when {
        ctx.tensor() != null -> fromTensor(ctx.tensor())
        ctx.NUMBER() != null -> NumberLiteral(ctx.NUMBER().text.toDouble())
        ctx.ID() != null -> IdRef(ctx.ID().text)
        ctx.expr() != null -> ParenExpr(fromExpr(ctx.expr()))
        else -> error("Unknown primary: ${ctx.text}")
    }

    private fun fromTensor(ctx: TensorDslParser.TensorContext): TensorLiteral {
        val elements = ctx.elements()?.expr()?.map { fromExpr(it) } ?: emptyList()
        return TensorLiteral(elements)
    }

    private fun fromIfStmt(ctx: TensorDslParser.IfStmtContext): IfStmt {
        val cond = fromCondition(ctx.condition())
        val thenBranch = mutableListOf<Statement>()
        val elseBranch = mutableListOf<Statement>()
        var sawElse = false
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child.text == "else") {
                sawElse = true
                continue
            }
            if (child is TensorDslParser.StatementContext) {
                if (sawElse) elseBranch.add(fromStatement(child))
                else thenBranch.add(fromStatement(child))
            }
        }
        return IfStmt(cond, thenBranch, if (sawElse) elseBranch else null)
    }

    private fun fromWhileStmt(ctx: TensorDslParser.WhileStmtContext): WhileStmt {
        val cond = fromCondition(ctx.condition())
        val body = ctx.statement().map { fromStatement(it) }
        return WhileStmt(cond, body)
    }

    private fun fromCondition(ctx: TensorDslParser.ConditionContext): Condition {
        val left = fromExpr(ctx.expr(0))
        val op = fromCompOp(ctx.compOp())
        val right = fromExpr(ctx.expr(1))
        return Condition(left, op, right)
    }

    private fun fromCompOp(ctx: TensorDslParser.CompOpContext): CompOp = when (ctx.text) {
        "==" -> CompOp.Eq
        "!=" -> CompOp.Neq
        "<" -> CompOp.Lt
        "<=" -> CompOp.Le
        ">" -> CompOp.Gt
        ">=" -> CompOp.Ge
        else -> error("Unknown comparison operator: ${ctx.text}")
    }

    fun checkVariableDeclarations(ast: Program): Program? {
        val declared = mutableSetOf<String>()

        fun checkExpr(expr: Expr): Boolean = when (expr) {
            is NumberLiteral -> true
            is IdRef -> declared.contains(expr.name)
            is TensorLiteral -> expr.elements.all { checkExpr(it) }
            is BinaryOp -> checkExpr(expr.left) && checkExpr(expr.right)
            is UnaryOp -> checkExpr(expr.expr)
            is ParenExpr -> checkExpr(expr.expr)
            is IndexOp -> checkExpr(expr.expr) && checkExpr(expr.index)
        }

        fun checkCondition(condition: Condition): Boolean =
            checkExpr(condition.left) && checkExpr(condition.right)

        fun checkStmt(stmt: Statement): Boolean = when (stmt) {
            is Declaration -> {
                if (declared.contains(stmt.id)) {
                    println("Error: Variable '${stmt.id}' redeclared.")
                    false
                } else {
                    val ok = stmt.expr?.let { checkExpr(it) } ?: true
                    declared.add(stmt.id)
                    ok
                }
            }
            is Assignment -> {
                if (!declared.contains(stmt.id)) {
                    println("Error: Variable '${stmt.id}' assigned before declaration.")
                    false
                } else {
                    checkExpr(stmt.expr)
                }
            }
            is PrintStmt -> checkExpr(stmt.expr)
            is Phi -> true // Phi nodes are generated correctly by SSA
            is IfStmt -> checkCondition(stmt.cond) &&
                stmt.thenBranch.all { checkStmt(it) } &&
                (stmt.elseBranch?.all { checkStmt(it) } ?: true)
            is WhileStmt -> checkCondition(stmt.cond) && stmt.body.all { checkStmt(it) }
        }
        return if (ast.statements.all { checkStmt(it) }) ast else null
    }
}
