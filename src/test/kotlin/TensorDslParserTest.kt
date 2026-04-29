package dk.sdu

import TensorDslLexer
import TensorDslParser
import org.antlr.v4.runtime.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
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
            a = [1, 2, 3];
            b = [4, 5, 6];
            c = a # b;
            print(c);
        """.trimIndent()
        assertDoesNotThrow { parse(code) }
    }

    @Test
    fun testValidAdditionAndPrint() {
        val code = "x = [1, 2] + [3, 4]; print(x);"
        assertDoesNotThrow { parse(code) }
    }

    @Test
    fun testValidUnaryAndTensor() {
        val code = "z = -([1, 2, 3] # [4, 5, 6]); print(z);"
        assertDoesNotThrow { parse(code) }
    }

    @Test
    fun testPostfixOperations() {
        val code = """
            v = [1, 2, 3];
            v_len = v->len;
            t = v->tpos;
            d = v->dim;
            first = v->0;
            second = v->1;
            print(first);
        """.trimIndent()
        assertDoesNotThrow { parse(code) }
    }
}

