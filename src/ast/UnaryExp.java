package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class UnaryExp implements Node {
    public enum Type { PRIMARY, FUNC_CALL, OP_EXP }
    private final Type type;

    // for PrimaryExp
    private PrimaryExp primaryExp;

    // for Ident '(' [FuncRParams] ')'
    private Ident funcName;
    private Terminal lParen;
    private FuncRParams funcRParams; // Can be null
    private Terminal rParen;

    // for UnaryOp UnaryExp
    private UnaryOp unaryOp;
    private UnaryExp unaryExp;

    // Constructor for PrimaryExp
    public UnaryExp(PrimaryExp primaryExp) {
        this.type = Type.PRIMARY;
        this.primaryExp = primaryExp;
    }

    // Constructor for function call
    public UnaryExp(Ident funcName, Terminal lParen, FuncRParams funcRParams, Terminal rParen) {
        this.type = Type.FUNC_CALL;
        this.funcName = funcName;
        this.lParen = lParen;
        this.funcRParams = funcRParams;
        this.rParen = rParen;
    }

    // Constructor for unary operation
    public UnaryExp(UnaryOp unaryOp, UnaryExp unaryExp) {
        this.type = Type.OP_EXP;
        this.unaryOp = unaryOp;
        this.unaryExp = unaryExp;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        switch (type) {
            case PRIMARY:
                return primaryExp.inferType(manager);
            case OP_EXP:
                SymbolEntry.Type operandType = unaryExp.inferType(manager);
                if (operandType == null) {
                    return null;
                }
                return SymbolEntry.Type.Int;
            case FUNC_CALL:
                SymbolEntry funcEntry = manager.findSymbol(funcName.getName());
                if (funcEntry == null) {
                    return null;
                }
                return funcEntry.getType();
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case PRIMARY:
                sb.append(primaryExp.toString());
                break;
            case FUNC_CALL:
                sb.append(funcName.toString());
                sb.append(lParen.toString());
                if (funcRParams != null) {
                    sb.append(funcRParams.toString());
                }
                sb.append(rParen.toString());
                break;
            case OP_EXP:
                sb.append(unaryOp.toString());
                sb.append(unaryExp.toString());
                break;
        }
        sb.append("<UnaryExp>\n");
        return sb.toString();
    }

    public Type getType() {
        return type;
    }

    public PrimaryExp getPrimaryExp() {
        return primaryExp;
    }

    public UnaryExp getUnaryExp() {
        return unaryExp;
    }

    public UnaryOp getUnaryOp() {
        return unaryOp;
    }

    public  FuncRParams getFuncRParams() {
        return  funcRParams;
    }

    public Ident getFuncName() {
        return funcName;
    }
}