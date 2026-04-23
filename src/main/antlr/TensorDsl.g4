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
    ;

assignment
    : ID '=' expr
    ;

printStmt
    : 'print' '(' expr ')'
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

// operator token (optional explicit)
TENSOR_OP : '#';

WS
    : [ \t\r\n]+ -> skip
    ;
