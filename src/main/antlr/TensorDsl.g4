grammar TensorDsl;

options { package=dk.sdu; }

// ----------------------
// Parser rules
// ----------------------

program
    : statement* EOF
    ;

statement
    : assignment
    | printStmt
    | ifStmt
    | whileStmt
    ;

assignment
    : ID '=' expr
    ;

printStmt
    : 'print' '(' expr ')'
    ;

ifStmt
    : 'if' '(' condition ')' '{' statement* '}' ( 'else' '{' statement* '}' )?
    ;

whileStmt
    : 'while' '(' condition ')' '{' statement* '}'
    ;

condition
    : expr compOp expr
    ;

compOp
    : '=='
    | '!='
    | '<'
    | '<='
    | '>'
    | '>='
    ;

// ----------------------
// Expressions (precedence)
// ----------------------

expr
    : addition
    ;

// lowest precedence
addition
    : tensorProduct (('+' | '-') tensorProduct)*
    ;

// tensor product level
tensorProduct
    : multiplication ( '#' multiplication )*
    ;

// multiplication level
multiplication
    : unary (('*' | '/') unary)*
    ;

unary
    : '-' unary
    | primary
    | primary T_TRANSPOSE
    | LENGTH '(' primary ')'
    | DIM '(' primary ')'
    ;

primary
    : tensor
    | NUMBER
    | ID
    | '(' expr ')'
    ;

// ----------------------
// Tensor literals
// ----------------------

tensor
    : '[' elements? ']'
    ;

elements
    : expr (',' expr)*
    ;

// ----------------------
// Lexer rules
// ----------------------

PRINT : 'print';

ID
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

NUMBER
    : [0-9]+ ('.' [0-9]+)?
    ;

TENSOR_OP : '#';

T_TRANSPOSE : 'tpos';

LENGTH : 'len';
DIM : 'dim';

WS
    : [ \t\r\n]+ -> skip
    ;
