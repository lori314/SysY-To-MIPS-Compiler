package ast;

import midend.ir.values.Value;

public class FuncDef implements Node {
    private FuncType funcType;
    private Ident ident;
    private Terminal lparen;
    private FuncFParams funcFParams;
    private Terminal rparen;
    private Block block;

    public FuncDef(FuncType funcType, Ident ident, Terminal lparen, FuncFParams funcFParams, Terminal rparen, Block block) {
        this.funcType = funcType;
        this.ident = ident;
        this.lparen = lparen;
        this.funcFParams = funcFParams;
        this.rparen = rparen;
        this.block = block;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(funcType.toString());
        sb.append(ident.toString());
        sb.append(lparen.toString());
        if (funcFParams != null) {
            sb.append(funcFParams.toString());
        }
        sb.append(rparen.toString());
        sb.append(block.toString());
        sb.append("<FuncDef>\n");
        return sb.toString();
    }

    public Ident getIdent() {
        return ident;
    }

    public FuncType getFuncType() {
        return funcType;
    }

    public FuncFParams getFuncFParams() {
        return funcFParams;
    }

    public Block getBlock() {
        return  block;
    }
}
