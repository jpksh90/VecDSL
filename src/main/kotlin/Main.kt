import dataflow.dumpCfgToDot
import dsl.TensorAstBuilder
import dsl.TensorCppArmadilloGenerator
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.nio.file.Paths

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: <program> <input-file> [--dump-cfg <dotfile>] [output-cpp-file]")
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
    println(prettyPrintAst(ast))

    // --- Optionally dump CFG to dot file ---
    val dumpIdx = args.indexOf("--dump-cfg")
    if (dumpIdx != -1 && dumpIdx + 1 < args.size) {
        val dotFile = args[dumpIdx + 1]
        ast.dumpCfgToDot(dotFile)
        println("CFG dumped to: $dotFile")
    }

    // --- Armadillo C++ code generation ---
    val outputCpp = if (args.size > 1 && args[1] != "--dump-cfg") args[1] else if (args.size > 3 && args[3] != "--dump-cfg") args[3] else "armadillo_out.cpp"
    val cppCode = TensorCppArmadilloGenerator.generate(ast)
    val outFile = File(outputCpp)
    outFile.writeText(cppCode)
    println("C++ Armadillo code generated in: ${outFile.absolutePath}")
}
