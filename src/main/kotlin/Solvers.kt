// Abstract powerset lattice for Gen-Kill framework
data class PowerSetLattice<T>(val universe: Set<T>) : Lattice<Set<T>> {
    override fun bottom(): Set<T> = emptySet()
    override fun join(a: Set<T>, b: Set<T>): Set<T> = a union b
    override fun lessThanOrEqual(a: Set<T>, b: Set<T>): Boolean = a.all { it in b }
}

// Gen-Kill data for each node
class GenKillData<T>(val gen: List<Set<T>>, val kill: List<Set<T>>)

// Worklist solver for forward dataflow analysis
typealias Transfer<T> = (inFact: Set<T>, gen: Set<T>, kill: Set<T>) -> Set<T>

fun <T> worklistSolver(
    cfg: CFG,
    lattice: Lattice<Set<T>>,
    genKill: GenKillData<T>,
    entryFact: Set<T> = emptySet(),
    transfer: Transfer<T> = { inFact, gen, kill -> (inFact - kill) + gen }
): List<Set<T>> {
    val n = cfg.nodes.size
    val inFacts = MutableList(n) { lattice.bottom() }
    val outFacts = MutableList(n) { lattice.bottom() }
    val worklist = ArrayDeque<Int>()
    inFacts[0] = entryFact
    worklist.add(0)
    while (worklist.isNotEmpty()) {
        val i = worklist.removeFirst()
        val node = cfg.nodes[i]
        val inFact = node.preds.fold(lattice.bottom()) { acc, p -> lattice.join(acc, outFacts[p]) }
        val outFact = transfer(inFact, genKill.gen[i], genKill.kill[i])
        if (outFact != outFacts[i]) {
            outFacts[i] = outFact
            for (s in node.succs) worklist.add(s)
        }
    }
    return outFacts
}


fun Program.genKillForReachingDefs(): GenKillData<Pair<String, Int>> {
    val n = statements.size
    val gen = List(n) { idx ->
        statements[idx].definedVars().map { it to idx }.toSet()
    }
    val kill = List(n) { idx ->
        val name = statements[idx].definedVars().firstOrNull()
        if (name != null) (0 until n).filter { j ->
            j != idx && statements[j].definedVars().contains(name)
        }.map { name to it }.toSet() else emptySet()
    }
    return GenKillData(gen, kill)
}

