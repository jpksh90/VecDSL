package dsl

import TensorDslParser

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
        ctx.assignment() != null -> fromAssignment(ctx.assignment())
        ctx.printStmt() != null -> fromPrintStmt(ctx.printStmt())
        ctx.ifStmt() != null -> fromIfStmt(ctx.ifStmt())
        ctx.whileStmt() != null -> fromWhileStmt(ctx.whileStmt())
        else -> error("Unknown statement: ${ctx.text}")
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
                    if (opCtx.NUMBER() != null) {
                        IndexOp(expr, NumberLiteral(opCtx.NUMBER().text.toDouble()))
                    } else if (opCtx.ID() != null) {
                        IndexOp(expr, IdRef(opCtx.ID().text))
                    } else {
                        error("Unknown postfix op: ${opCtx.text}")
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
        fun checkStmt(stmt: Statement): Boolean = when (stmt) {
            is Assignment -> {
                val ok = checkExpr(stmt.expr)
                declared.add(stmt.id)
                ok
            }
            is PrintStmt -> checkExpr(stmt.expr)
            is IfStmt -> stmt.thenBranch.all { checkStmt(it) } && (stmt.elseBranch?.all { checkStmt(it) } ?: true)
            is WhileStmt -> stmt.body.all { checkStmt(it) }
        }
        return if (ast.statements.all { checkStmt(it) }) ast else null
    }
}

object TensorCppArmadilloGenerator {
    fun generate(ast: TensorAstNode): String = buildString {
        append("#include <armadillo>\n#include <iostream>\nusing namespace arma;\nusing namespace std;\n\n")
        append("int main() {\n")
        append(generateStatements(ast))
        append("    return 0;\n}")
    }

    private fun generateStatements(node: TensorAstNode, indent: String = "    "): String = when (node) {
        is Program -> node.statements.joinToString("") { generateStatements(it, indent) }
        is Assignment -> "$indent auto ${node.id} = ${generateExpr(node.expr)};\n"
        is PrintStmt -> "$indent cout << ${generateExpr(node.expr)} << endl;\n"
        is IfStmt -> buildString {
            append("$indent if (" + generateCondition(node.cond) + ") {\n")
            append(node.thenBranch.joinToString("") { generateStatements(it, indent + "    ") })
            append("$indent}")
            if (node.elseBranch != null) {
                append(" else {\n")
                append(node.elseBranch.joinToString("") { generateStatements(it, indent + "    ") })
                append("$indent}")
            }
            append("\n")
        }
        is WhileStmt -> buildString {
            append("$indent while (" + generateCondition(node.cond) + ") {\n")
            append(node.body.joinToString("") { generateStatements(it, indent + "    ") })
            append("$indent}\n")
        }
        else -> ""
    }

    private fun generateCondition(cond: Condition): String =
        "(" + generateExpr(cond.left) + " " + compOpToCpp(cond.op) + " " + generateExpr(cond.right) + ")"

    private fun compOpToCpp(op: CompOp): String = when (op) {
        is CompOp.Eq -> "=="
        is CompOp.Neq -> "!="
        is CompOp.Lt -> "<"
        is CompOp.Le -> "<="
        is CompOp.Gt -> ">"
        is CompOp.Ge -> ">="
    }

    private fun generateExpr(expr: Expr): String = when (expr) {
        is NumberLiteral -> if (expr.value == expr.value.toLong().toDouble()) expr.value.toLong().toString() else expr.value.toString()
        is IdRef -> expr.name
        is TensorLiteral -> {
            val elems = expr.elements.joinToString(", ") { generateExpr(it) }
            "vec({$elems})"
        }
        is BinaryOp -> {
            val left = generateExpr(expr.left)
            val right = generateExpr(expr.right)
            when (expr.op) {
                Op.Plus -> "$left + $right"
                Op.Minus -> "$left - $right"
                Op.Times -> "$left * $right"
                Op.Div -> "$left / $right"
                Op.TensorProd -> "$left % $right" // Armadillo element-wise product
                else -> error("Unknown binary op: ${expr.op}")
            }
        }
        is UnaryOp -> when (expr.op) {
            Op.Minus -> "-(${generateExpr(expr.expr)})"
            Op.Transpose -> "(${generateExpr(expr.expr)}).t()"
            Op.Length -> "${generateExpr(expr.expr)}.n_elem"
            Op.Dim -> "${generateExpr(expr.expr)}.n_rows" // or n_cols, depending on semantics
            else -> error("Unknown unary op: ${expr.op}")
        }
        is ParenExpr -> "(${generateExpr(expr.expr)})"
        is IndexOp -> "(${generateExpr(expr.expr)}.at(${generateExpr(expr.index)}))"
    }
}
