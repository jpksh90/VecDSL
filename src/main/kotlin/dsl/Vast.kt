package dsl

// AST node hierarchy
sealed class TensorAstNode

sealed class Statement : TensorAstNode()
data class Assignment(val id: String, val expr: Expr) : Statement()
data class PrintStmt(val expr: Expr) : Statement()
data class IfStmt(val cond: Condition, val thenBranch: List<Statement>, val elseBranch: List<Statement>?) : Statement()
data class WhileStmt(val cond: Condition, val body: List<Statement>) : Statement()

data class Program(val statements: List<Statement>) : TensorAstNode()

data class Condition(val left: Expr, val op: CompOp, val right: Expr) : TensorAstNode()
sealed class CompOp : TensorAstNode() {
    object Eq : CompOp()
    object Neq : CompOp()
    object Lt : CompOp()
    object Le : CompOp()
    object Gt : CompOp()
    object Ge : CompOp()
}

sealed class Expr : TensorAstNode()
data class NumberLiteral(val value: Double) : Expr()
data class IdRef(val name: String) : Expr()
data class TensorLiteral(val elements: List<Expr>) : Expr()
data class BinaryOp(val left: Expr, val op: Op, val right: Expr) : Expr()
data class UnaryOp(val op: Op, val expr: Expr) : Expr()
data class ParenExpr(val expr: Expr) : Expr()
data class IndexOp(val expr: Expr, val index: Expr) : Expr()

sealed class Op {
    object Plus : Op()
    object Minus : Op()
    object Times : Op()
    object Div : Op()
    object TensorProd : Op()
    object Transpose : Op()
    object Length : Op()
    object Dim : Op()
}
