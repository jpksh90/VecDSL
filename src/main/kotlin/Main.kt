package dk.sdu

import TensorAstNode
import Program
import Assignment
import PrintStmt
import NumberLiteral
import IdRef
import TensorLiteral
import BinaryOp
import UnaryOp
import ParenExpr
import Op
import TensorDslLexer
import TensorDslParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.nio.file.Paths
import CompOp
import Condition

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: <program> <input-file>")
        return
    }
    // Normalize path to avoid path traversal warning
    val normalizedPath = Paths.get(args[0]).normalize().toFile()
    if (!normalizedPath.exists()) {
        println("File not found: ${args[0]}")
        return
    }
    val input = normalizedPath.readText()
    val lexer = TensorDslLexer(CharStreams.fromString(input))
    val tokens = CommonTokenStream(lexer)
    val parser = TensorDslParser(tokens)
    val tree = parser.program()
    val ast = TensorAstBuilder.fromProgram(tree)
    if (ast == null) {
        println("Failed to build AST")
        return
    }
    println(prettyPrintAst(ast as TensorAstNode))

    // --- Armadillo C++ code generation ---
    val outputCpp = if (args.size > 1) args[1] else "armadillo_out.cpp"
    val cppCode = TensorCppArmadilloGenerator.generate(ast)
    val outFile = File(outputCpp)
    outFile.writeText(cppCode)
    println("C++ Armadillo code generated in: ${outFile.absolutePath}")
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
    is Condition -> "${prettyPrintAst(node.left)} ${prettyPrintCompOp(node.op)} ${prettyPrintAst(node.right)}"
    else -> error("Unknown AST node type: ${node::class.simpleName}")
}

fun prettyPrintOp(op: Op): String = when (op) {
    is Op.Plus -> "+"
    is Op.Minus -> "-"
    is Op.Times -> "*"
    is Op.Div -> "/"
    is Op.TensorProd -> "#"
}

fun prettyPrintCompOp(op: CompOp): String = when (op) {
    is CompOp.Eq -> "=="
    is CompOp.Neq -> "!="
    is CompOp.Lt -> "<"
    is CompOp.Le -> "<="
    is CompOp.Gt -> ">"
    is CompOp.Ge -> ">="
}