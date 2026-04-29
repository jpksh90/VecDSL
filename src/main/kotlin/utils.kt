import dsl.Assignment
import dsl.BinaryOp
import dsl.CompOp
import dsl.Condition
import dsl.IdRef
import dsl.IfStmt
import dsl.IndexOp
import dsl.NumberLiteral
import dsl.Op
import dsl.ParenExpr
import dsl.PrintStmt
import dsl.Program
import dsl.TensorAstNode
import dsl.TensorLiteral
import dsl.UnaryOp
import dsl.WhileStmt

fun prettyPrintAst(node: TensorAstNode, indent: String = ""): String = when (node) {
    is Program -> node.statements.joinToString("\n") { prettyPrintAst(it, indent) }
    is Assignment -> "$indent${node.id} = ${prettyPrintAst(node.expr)}"
    is PrintStmt -> "$indent print(${prettyPrintAst(node.expr)})"
    is NumberLiteral -> if (node.value == node.value.toLong().toDouble()) node.value.toLong().toString() else node.value.toString()
    is IdRef -> node.name
    is TensorLiteral -> node.elements.joinToString(prefix = "[", postfix = "]", separator = ", ") { prettyPrintAst(it) }
    is BinaryOp -> "${prettyPrintAst(node.left)} ${prettyPrintOp(node.op)} ${prettyPrintAst(node.right)}"
    is UnaryOp -> when (node.op) {
        is Op.Minus -> "-${prettyPrintAst(node.expr)}"
        is Op.Transpose -> "${prettyPrintAst(node.expr)}->tpos"
        is Op.Length -> "${prettyPrintAst(node.expr)}->len"
        is Op.Dim -> "${prettyPrintAst(node.expr)}->dim"
        else -> error("Unknown unary op: ${node.op}")
    }
    is ParenExpr -> "(${prettyPrintAst(node.expr)})"
    is Condition -> "${prettyPrintAst(node.left)} ${prettyPrintCompOp(node.op)} ${prettyPrintAst(node.right)}"
    is WhileStmt -> buildString {
        append("${indent}while (" + prettyPrintAst(node.cond) + ") {\n")
        append(node.body.joinToString("\n") { prettyPrintAst(it, indent + "    ") })
        append("\n$indent}")
    }
    is IfStmt -> buildString {
        append("${indent}if (" + prettyPrintAst(node.cond) + ") {\n")
        append(node.thenBranch.joinToString("\n") { prettyPrintAst(it, indent + "    ") })
        append("\n$indent}")
        if (node.elseBranch != null) {
            append(" else {\n")
            append(node.elseBranch.joinToString("\n") { prettyPrintAst(it, indent + "    ") })
            append("\n$indent}")
        }
    }
    is IndexOp -> "${prettyPrintAst(node.expr)}->${prettyPrintAst(node.index)}"
    else -> error("Unknown AST node type: ${node::class.simpleName}")
}

fun prettyPrintOp(op: Op): String = when (op) {
    is Op.Plus -> "+"
    is Op.Minus -> "-"
    is Op.Times -> "*"
    is Op.Div -> "/"
    is Op.TensorProd -> "#"
    is Op.Transpose -> "tpos"
    is Op.Length -> "len"
    is Op.Dim -> "dim"
}

fun prettyPrintCompOp(op: CompOp): String = when (op) {
    is CompOp.Eq -> "=="
    is CompOp.Neq -> "!="
    is CompOp.Lt -> "<"
    is CompOp.Le -> "<="
    is CompOp.Gt -> ">"
    is CompOp.Ge -> ">="
}
