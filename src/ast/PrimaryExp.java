package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class PrimaryExp implements Node {

    public enum Type { EXP, LVAL, NUMBER }
    private final Type type;

    // for '(' Exp ')'
    private Terminal lParen;
    private Exp exp;
    private Terminal rParen;

    // for LVal
    private LVal lVal;

    // for Number
    private Number number;

    // Constructor for '(' Exp ')'
    public PrimaryExp(Terminal lParen, Exp exp, Terminal rParen) {
        this.type = Type.EXP;
        this.lParen = lParen;
        this.exp = exp;
        this.rParen = rParen;
    }

    // Constructor for LVal
    public PrimaryExp(LVal lVal) {
        this.type = Type.LVAL;
        this.lVal = lVal;
    }

    // Constructor for Number
    public PrimaryExp(Number number) {
        this.type = Type.NUMBER;
        this.number = number;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        switch (type) {
            case EXP:
                return exp.inferType(manager);
            case NUMBER:
                return SymbolEntry.Type.Int;
            case LVAL:
                return lVal.inferType(manager);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case EXP:
                sb.append(lParen.toString());
                sb.append(exp.toString());
                sb.append(rParen.toString());
                break;
            case LVAL:
                sb.append(lVal.toString());
                break;
            case NUMBER:
                sb.append(number.toString());
                break;
        }
        sb.append("<PrimaryExp>\n");
        return sb.toString();
    }

    public Type getType() {
        return type;
    }

    public Number getNumber() {
        return number;
    }

    public LVal getLVal() {
        return lVal;
    }

    public Exp getExp() {
        return exp;
    }
}
