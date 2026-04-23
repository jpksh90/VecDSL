package dk.sdu

// Abstract Syntax Tree (AST) definitions for TensorDSL

sealed class TensorAstNode

class Program(val statements: List<Statement>) : TensorAstNode()

sealed class Statement : TensorAstNode()
class Assignment(val id: String, val expr: Expr) : Statement()
class PrintStmt(val expr: Expr) : Statement()

sealed class Expr : TensorAstNode()
class NumberLiteral(val value: Double) : Expr()
class IdRef(val name: String) : Expr()
class TensorLiteral(val elements: List<Expr>) : Expr()
class BinaryOp(val left: Expr, val op: Op, val right: Expr) : Expr()
class UnaryOp(val op: Op, val expr: Expr) : Expr()
class ParenExpr(val expr: Expr) : Expr()

sealed class Op {
    object Plus : Op()
    object Minus : Op()
    object Times : Op()
    object Div : Op()
    object TensorProd : Op()
}

