// Basic block representation for CFG
data class BasicBlock(val index: Int, val statements: List<Statement>, val preds: MutableList<Int> = mutableListOf(), val succs: MutableList<Int> = mutableListOf())

// Build basic blocks from a flat list of statements (trivial version: one block for all statements)
fun Program.toBasicBlocks(): List<BasicBlock> {
    // For now, treat the whole program as a single block (extend for real branches/labels)
    return listOf(BasicBlock(0, statements))
}

// Build a block-level CFG (trivial version: single block, no edges)
fun Program.toBlockCFG(): CFG {
    val blocks = toBasicBlocks()
    val nodes = blocks.map { block ->
        CFG.CFGNode(block.index, block.preds, block.succs)
    }
    return CFG(nodes)
}

// Control Flow Graph (CFG) representation
class CFG(val nodes: List<CFGNode>) {
    data class CFGNode(val index: Int, val preds: List<Int>, val succs: List<Int>)
}

fun Program.toCFG(): CFG {
    val n = statements.size
    val nodes = List(n) { idx ->
        val preds = if (idx == 0) emptyList() else listOf(idx - 1)
        val succs = if (idx == n - 1) emptyList() else listOf(idx + 1)
        CFG.CFGNode(idx, preds, succs)
    }
    return CFG(nodes)
}

