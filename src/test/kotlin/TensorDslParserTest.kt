package dk.sdu

import TensorDslLexer
import TensorDslParser
import dsl.TensorAstBuilder
import org.antlr.v4.runtime.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TensorDslParserTest {
    private fun parse(input: String) {
        val lexer = TensorDslLexer(CharStreams.fromString(input))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
            ) {
                throw RuntimeException("line $line:$charPositionInLine $msg")
            }
        })
        parser.program()
    }

    @Test
    fun testValidAssignmentAndTensorProduct() {
        val code = """
            let a = [1, 2, 3];
            let b = [4, 5, 6];
            let c = a # b;
            print(c);
        """.trimIndent()
        assertDoesNotThrow { parse(code) }
    }

    @Test
    fun testValidAdditionAndPrint() {
        val code = "let x = [1, 2] + [3, 4]; print(x);"
        assertDoesNotThrow { parse(code) }
    }

    @Test
    fun testValidUnaryAndTensor() {
        val code = "let z = -([1, 2, 3] # [4, 5, 6]); print(z);"
        assertDoesNotThrow { parse(code) }
    }

    @Test
    fun testPostfixOperations() {
        val code = """
            let v = [1, 2, 3];
            let v_len = v->len;
            let t = v->tpos;
            let d = v->dim;
            let first = v->0;
            let second = v->1;
            print(first);
        """.trimIndent()
        assertDoesNotThrow { parse(code) }
    }

    @Test
    fun testRedeclarationIsRejectedDuringSemanticCheck() {
        val code = """
            let a = [1, 2, 3];
            let b = a;
            let a = b;
        """.trimIndent()

        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)

        assertNull(TensorAstBuilder.fromProgram(parser.program()))
    }

    @Test
    fun testUndeclaredVariableUseIsRejectedDuringSemanticCheck() {
        val code = """
            let a = b;
            print(a);
        """.trimIndent()

        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)

        assertNull(TensorAstBuilder.fromProgram(parser.program()))
    }

    @Test
    fun testAssignmentBeforeDeclarationIsRejectedDuringSemanticCheck() {
        val code = """
            a = 1;
            print(a);
        """.trimIndent()

        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)

        assertNull(TensorAstBuilder.fromProgram(parser.program()))
    }
}
