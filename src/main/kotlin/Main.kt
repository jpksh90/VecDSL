package dk.sdu

import TensorDslLexer
import TensorDslParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: <program> <input-file>")
        return
    }
    val inputFile = File(args[0])
    if (!inputFile.exists()) {
        println("File not found: ${args[0]}")
        return
    }
    val input = inputFile.readText()
    val lexer = TensorDslLexer(CharStreams.fromString(input))
    val tokens = CommonTokenStream(lexer)
    val parser = TensorDslParser(tokens)
    val tree = parser.program()
    val ast = TensorAstBuilder.fromProgram(tree)
    println(prettyPrintAst(ast))
}

fun prettyPrintAst(node: TensorAstNode, indent: String = ""): String = when (node) {
    is Program -> node.statements.joinToString("\n") { prettyPrintAst(it, indent) }
    is Assignment -> "$indent${node.id} = ${prettyPrintAst(node.expr)};"
    is PrintStmt -> "$indent print(${prettyPrintAst(node.expr)});"
    is NumberLiteral -> node.value.toString()
    is IdRef -> node.name
    is TensorLiteral -> node.elements.joinToString(prefix = "[", postfix = "]", separator = ", ") { prettyPrintAst(it) }
    is BinaryOp -> "${prettyPrintAst(node.left)} ${prettyPrintOp(node.op)} ${prettyPrintAst(node.right)}"
    is UnaryOp -> "-${prettyPrintAst(node.expr)}"
    is ParenExpr -> "(${prettyPrintAst(node.expr)})"
}

fun prettyPrintOp(op: Op): String = when (op) {
    Op.Plus -> "+"
    Op.Minus -> "-"
    Op.Times -> "*"
    Op.Div -> "/"
    Op.TensorProd -> "#"
}