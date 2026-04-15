package frontend;

public enum TokenType {
    // 标识符, 整型常量, 字符串常量
    IDENFR, INTCON, STRCON,

    // 关键字
    CONSTTK, INTTK, VOIDTK,
    IFTK, ELSETK, FORTK,
    BREAKTK, CONTINUETK, RETURNTK,
    MAINTK, PRINTFTK, STATICTK, 

    // 运算符
    PLUS, MINU, MULT, DIV, MOD,
    LSS, LEQ, GRE, GEQ, EQL, NEQ,
    ASSIGN, NOT, AND, OR,

    // 分隔符
    SEMICN, COMMA,
    LPARENT, RPARENT,
    LBRACK, RBRACK,
    LBRACE, RBRACE;
}