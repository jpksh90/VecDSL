package dataflow

import dsl.IfStmt
import dsl.Program
import dsl.Statement
import dsl.WhileStmt

class CFG(val stmts: List<Statement>, val nodes: List<CFGNode>) {
    data class CFGNode(val index: Int, val preds: List<Int>, val succs: List<Int>, val edgeLabels: Map<Int, String> = emptyMap())
}

class BasicBlock(val index: Int) {
    val statements = mutableListOf<Statement>()
    val preds = mutableListOf<Int>()
    val succs = mutableListOf<Int>()
    val edgeLabels = mutableMapOf<Int, String>()
}

class BlockCFG(val blocks: List<BasicBlock>)

fun Program.toBlockCFG(): BlockCFG {
    val blocks = mutableListOf<BasicBlock>()

    fun createBlock(): BasicBlock {
        val b = BasicBlock(blocks.size)
        blocks.add(b)
        return b
    }

    fun addEdge(from: BasicBlock, to: BasicBlock, label: String? = null) {
        if (!from.succs.contains(to.index)) {
            from.succs.add(to.index)
            if (label != null) from.edgeLabels[to.index] = label
        }
        if (!to.preds.contains(from.index)) to.preds.add(from.index)
    }

    fun build(stmts: List<Statement>, currentBlock: BasicBlock, exitBlock: BasicBlock?): BasicBlock {
        var curr = currentBlock
        for (stmt in stmts) {
            when (stmt) {
                is IfStmt -> {
                    curr.statements.add(stmt)
                    val thenEntry = createBlock()
                    val elseEntry = if (stmt.elseBranch != null) createBlock() else null
                    val joinBlock = createBlock()

                    addEdge(curr, thenEntry, "true")
                    build(stmt.thenBranch, thenEntry, joinBlock)

                    if (elseEntry != null) {
                        addEdge(curr, elseEntry, "false")
                        build(stmt.elseBranch!!, elseEntry, joinBlock)
                    } else {
                        addEdge(curr, joinBlock, "false")
                    }
                    curr = joinBlock
                }
                is WhileStmt -> {
                    val loopHeader = createBlock()
                    addEdge(curr, loopHeader)
                    loopHeader.statements.add(stmt)

                    val bodyEntry = createBlock()
                    val loopExit = createBlock()

                    addEdge(loopHeader, bodyEntry, "true")
                    addEdge(loopHeader, loopExit, "false")

                    build(stmt.body, bodyEntry, loopHeader)
                    curr = loopExit
                }
                else -> {
                    curr.statements.add(stmt)
                }
            }
        }
        if (exitBlock != null) {
            addEdge(curr, exitBlock)
        }
        return curr
    }

    val entry = createBlock()
    build(statements, entry, null)

    return BlockCFG(blocks)
}

fun Program.toCFG(): CFG {
    val allStmts = mutableListOf<Statement>()
    fun collect(stmts: List<Statement>) {
        for (stmt in stmts) {
            allStmts.add(stmt)
            when (stmt) {
                is IfStmt -> {
                    collect(stmt.thenBranch)
                    stmt.elseBranch?.let { collect(it) }
                }
                is WhileStmt -> {
                    collect(stmt.body)
                }
                else -> {}
            }
        }
    }
    collect(statements)

    val stmtToIndex = mutableMapOf<Statement, Int>()
    allStmts.forEachIndexed { index, statement -> stmtToIndex[statement] = index }

    val n = allStmts.size
    val preds = List(n) { mutableListOf<Int>() }
    val succs = List(n) { mutableListOf<Int>() }
    val edgeLabels = List(n) { mutableMapOf<Int, String>() }

    fun addEdge(from: Int, to: Int, label: String? = null) {
        if (from !in 0 until n || to !in 0 until n) return
        if (!succs[from].contains(to)) {
            succs[from].add(to)
            preds[to].add(from)
            if (label != null) edgeLabels[from][to] = label
        }
    }

    fun buildEdges(stmts: List<Statement>, nextIdx: Int?) {
        for (i in stmts.indices) {
            val stmt = stmts[i]
            val currentIdx = stmtToIndex[stmt]!!
            val afterIdx = if (i < stmts.size - 1) stmtToIndex[stmts[i + 1]] else nextIdx

            when (stmt) {
                is IfStmt -> {
                    // Then branch
                    if (stmt.thenBranch.isNotEmpty()) {
                        addEdge(currentIdx, stmtToIndex[stmt.thenBranch[0]]!!, "true")
                        buildEdges(stmt.thenBranch, afterIdx)
                    } else if (afterIdx != null) {
                        addEdge(currentIdx, afterIdx, "true")
                    }

                    // Else branch
                    if (stmt.elseBranch != null && stmt.elseBranch.isNotEmpty()) {
                        addEdge(currentIdx, stmtToIndex[stmt.elseBranch[0]]!!, "false")
                        buildEdges(stmt.elseBranch!!, afterIdx)
                    } else if (afterIdx != null) {
                        addEdge(currentIdx, afterIdx, "false")
                    }
                }
                is WhileStmt -> {
                    // Body
                    if (stmt.body.isNotEmpty()) {
                        addEdge(currentIdx, stmtToIndex[stmt.body[0]]!!, "true")
                        buildEdges(stmt.body, currentIdx)
                    }
                    // Loop exit
                    if (afterIdx != null) {
                        addEdge(currentIdx, afterIdx, "false")
                    }
                }
                else -> {
                    if (afterIdx != null) {
                        addEdge(currentIdx, afterIdx)
                    }
                }
            }
        }
    }

    buildEdges(statements, null)

    val nodes = List(n) { idx ->
        CFG.CFGNode(idx, preds[idx].toList(), succs[idx].toList(), edgeLabels[idx].toMap())
    }
    return CFG(allStmts, nodes)
}
