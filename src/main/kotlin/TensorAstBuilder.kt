package dk.sdu

object TensorAstBuilder {
    fun fromProgram(ctx: TensorDslParser.ProgramContext): Program {
        val statements = ctx.statement().map { fromStatement(it) }
        return Program(statements)
    }

    private fun fromStatement(ctx: TensorDslParser.StatementContext): Statement = when {
        ctx.assignment() != null -> fromAssignment(ctx.assignment())
        ctx.printStmt() != null -> fromPrintStmt(ctx.printStmt())
        else -> error("Unknown statement: ${ctx.text}")
    }

    private fun fromAssignment(ctx: TensorDslParser.AssignmentContext): Assignment {
        val id = ctx.ID().text
        val expr = fromExpr(ctx.expr())
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
            val opToken = ctx.getChild(i * 2 - 1).text
            val op = when (opToken) {
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
        ctx.primary() != null -> fromPrimary(ctx.primary())
        else -> error("Unknown unary: ${ctx.text}")
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
        else -> ""
    }

    private fun generateExpr(expr: Expr): String = when (expr) {
        is NumberLiteral -> expr.value.toString()
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
            }
        }
        is UnaryOp -> "-(${generateExpr(expr.expr)})"
        is ParenExpr -> "(${generateExpr(expr.expr)})"
    }
}
