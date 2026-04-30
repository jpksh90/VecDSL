package codegen

import dsl.*
import prettyPrintAst

fun generateExpr(expr: Expr): String = when (expr) {
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

object TensorCppArmadilloGenerator {
    fun generate(ast: TensorAstNode): String = buildString {
        append("#include <armadillo>\n#include <iostream>\nusing namespace arma;\nusing namespace std;\n\n")
        append("int main() {\n")
        append(generateStatements(ast))
        append("    return 0;\n}")
    }

    private fun generateStatements(node: TensorAstNode, indent: String = "    "): String = when (node) {
        is Program -> node.statements.joinToString("") { generateStatements(it, indent) }
        is Declaration -> if (node.expr != null) "$indent auto ${node.id} = ${generateExpr(node.expr)};\n" else "$indent /* let ${node.id}; */\n"
        is Assignment -> "$indent ${node.id} = ${generateExpr(node.expr)};\n"
        is PrintStmt -> "$indent cout << ${generateExpr(node.expr)} << endl;\n"
        is Phi -> "$indent // Phi: ${node.id} = phi(${node.versions.values.joinToString(", ")})\n"
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

}
