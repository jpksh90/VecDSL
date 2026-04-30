package dataflow

import TensorDslLexer
import TensorDslParser
import dsl.TensorAstBuilder
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import prettyPrintAst

class OptimizerTest {
    @Test
    fun testConstantFoldingAndDCE() {
        val code = """
            a = 1 + 2;
            b = a * 10;
            c = 100; -- dead assignment
            print(b);
        """.trimIndent()
        
        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        val program = TensorAstBuilder.fromProgram(parser.program())!!
        
        val optimizer = Optimizer(program)
        val optimized = optimizer.optimize()
        
        // Expected optimized code:
        // a = 3.0
        // b = 30.0
        // print(30.0)
        // c = 100 should be gone if DCE works
        
        val output = prettyPrintAst(optimized).trim()
        println("Optimized Code:\n$output")
        
        // Check that 'c' is gone
        assert(!output.contains("c = 100"))
        // Check that 'b' is folded in print
        assert(output.contains("print(30)"))
    }
}
