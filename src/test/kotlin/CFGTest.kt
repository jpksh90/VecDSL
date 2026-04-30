package dataflow

import TensorDslLexer
import TensorDslParser
import dsl.TensorAstBuilder
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import prettyPrintAst

class CFGTest {
    @Test
    fun testIfStmtCFG() {
        val code = """
            a = 1;
            if (a > 0) {
                b = 2;
            } else {
                c = 3;
            }
            d = 4;
        """.trimIndent()
        
        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        val program = TensorAstBuilder.fromProgram(parser.program())!!
        
        val cfg = program.toCFG()
        
        // Expected statements:
        // 0: a = 1
        // 1: if (a > 0)
        // 2: b = 2
        // 3: c = 3
        // 4: d = 4
        
        assertEquals(5, cfg.nodes.size)
        assertEquals(5, cfg.stmts.size)
        
        // a = 1 -> if (a > 0)
        assertEquals(listOf(1), cfg.nodes[0].succs)
        
        // if (a > 0) -> b = 2 (then) AND c = 3 (else)
        assertEquals(setOf(2, 3), cfg.nodes[1].succs.toSet())
        
        // b = 2 -> d = 4
        assertEquals(listOf(4), cfg.nodes[2].succs)
        
        // c = 3 -> d = 4
        assertEquals(listOf(4), cfg.nodes[3].succs)
        
        // d = 4 -> []
        assertEquals(emptyList<Int>(), cfg.nodes[4].succs)
    }

    @Test
    fun testWhileStmtCFG() {
        val code = """
            a = [0];
            limit = 10;
            while (a->0 < limit) {
                next = a + [1];
            }
            print(a);
        """.trimIndent()
        
        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        val program = TensorAstBuilder.fromProgram(parser.program())!!
        
        val cfg = program.toCFG()
        
        // Expected statements:
        // 0: a = [0]
        // 1: limit = 10
        // 2: while (a->0 < limit)
        // 3: next = a + [1]
        // 3: print(a)
        
        assertEquals(5, cfg.nodes.size)
        
        // a = [0] -> limit = 10
        assertEquals(listOf(1), cfg.nodes[0].succs)

        // limit = 10 -> while
        assertEquals(listOf(2), cfg.nodes[1].succs)
        
        // while -> next = a + [1] (body) AND print(a) (exit)
        assertEquals(setOf(3, 4), cfg.nodes[2].succs.toSet())
        
        // next = a + [1] -> while (loop back)
        assertEquals(listOf(2), cfg.nodes[3].succs)
        
        // print(a) -> []
        assertEquals(emptyList<Int>(), cfg.nodes[4].succs)
    }

    @Test
    fun testToBlockCFG() {
        val code = """
            a = 1;
            b = 2;
            if (a > 0) {
                c = 3;
            } else {
                d = 4;
            }
            e = 5;
        """.trimIndent()
        
        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        val program = TensorAstBuilder.fromProgram(parser.program())!!
        val blockCfg = program.toBlockCFG()

        // Expected blocks:

        assertEquals(4, blockCfg.blocks.size)
        assertEquals(3, blockCfg.blocks[0].statements.size) // a=1, b=2, if
        assertEquals(listOf(1, 2), blockCfg.blocks[0].succs.sorted())
        
        assertEquals(1, blockCfg.blocks[1].statements.size) // c=3
        assertEquals(listOf(3), blockCfg.blocks[1].succs)
        
        assertEquals(1, blockCfg.blocks[2].statements.size) // d=4
        assertEquals(listOf(3), blockCfg.blocks[2].succs)
        
        assertEquals(1, blockCfg.blocks[3].statements.size) // e=5
        assertEquals(emptyList<Int>(), blockCfg.blocks[3].succs)
    }

    @Test
    fun testConstantPropagation() {
        val code = """
            x = 10;
            y = x * 2;
            print(y);
        """.trimIndent()
        
        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        val program = TensorAstBuilder.fromProgram(parser.program())!!
        
        val env = program.constantPropagationEnv()

        assertEquals(ConstVec.Const(listOf(20.0)), env["y"])
    }

    @Test
    fun testLivenessAnalysis() {
        val code = """
            a = 1;
            b = 2;
            c = a + b;
            print(c);
        """.trimIndent()
        
        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        val program = TensorAstBuilder.fromProgram(parser.program())!!
        
        val liveness = program.livenessAnalysis()
        // Expected statements:
        // 0: a = 1 (live before: {})
        // 1: b = 2 (live before: {a})
        // 2: c = a + b (live before: {a, b})
        // 3: print(c) (live before: {c})
        
        assertEquals(4, liveness.size)
        assertEquals(emptySet<String>(), liveness[0].liveVars)
        assertEquals(setOf("a"), liveness[1].liveVars)
        assertEquals(setOf("a", "b"), liveness[2].liveVars)
        assertEquals(setOf("c"), liveness[3].liveVars)
    }

    @Test
    fun testAvailableExpressions() {
        val code = """
            a = 1;
            b = 2;
            x = a + b;
            y = a + b;
            c = 3;
            z = a + b;
        """.trimIndent()
        
        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        val program = TensorAstBuilder.fromProgram(parser.program())!!
        
        val available = program.availableExpressions()
        
        // At y = a + b (index 3), a + b should be available
        val cfg = program.toCFG()
        val allExprs = program.statements.flatMap { it.allSubExprs() }.filter { it !is dsl.IdRef && it !is dsl.NumberLiteral }.toSet()
        val lattice = MustPowerSetLattice(allExprs)
        val inStates = available.getInStates(cfg, lattice, Direction.FORWARD)
        
        val aPlusB = (program.statements[2] as dsl.Assignment).expr
        assert(inStates[3].contains(aPlusB))
        
        // At z = a + b (index 5), a + b should still be available
        assert(inStates[5].contains(aPlusB))
    }

    @Test
    fun testCSE() {
        val code = """
            a = 1;
            b = 2;
            x = a + b;
            y = a + b;
            print(y);
        """.trimIndent()
        
        val lexer = TensorDslLexer(CharStreams.fromString(code))
        val tokens = CommonTokenStream(lexer)
        val parser = TensorDslParser(tokens)
        val program = TensorAstBuilder.fromProgram(parser.program())!!
        
        val optimizer = Optimizer(program)
        val optimized = optimizer.optimize()
        
        val output = prettyPrintAst(optimized)
        println("CSE Optimized Code:\n$output")
    }

    @Test
    fun testRepeatedVariablesAreRejectedBeforeSsa() {
        val code = """
            a = [1, 2, 3];
            b = [4, 5, 6];
            c = [0, 0, 0];
            d = [0, 0, 1];
            c = b + d;
            i = 0;

            while (i < 3) {
                c = c + b;
                i = i + 1;
            }

            if (a->0 > 0) {
                d = c;
            } else {
                d = a;
            }
        """.trimIndent()

        val program = TensorAstBuilder.fromProgram(
            TensorDslParser(CommonTokenStream(TensorDslLexer(CharStreams.fromString(code)))).program()
        )

        assertNull(program)
    }
}
