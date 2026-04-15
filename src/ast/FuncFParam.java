package ast;

import midend.ir.values.Value;
import symbol.SymbolEntry;

public class FuncFParam implements Node {
    private BType type;
    private Ident ident;
    private Terminal lBrack;
    private Terminal rBrack;

    public FuncFParam(BType type, Ident ident, Terminal lBrack, Terminal rBrack) {
        this.type = type;
        this.ident = ident;
        this.lBrack = lBrack;
        this.rBrack = rBrack;
    }

    public SymbolEntry.Type getType() {
        if (lBrack != null) {
            return SymbolEntry.Type.IntArray;
        }
        else return SymbolEntry.Type.Int;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.toString());
        sb.append(ident.toString());
        if(lBrack != null) {
            sb.append(lBrack.toString());
            sb.append(rBrack.toString());
        }
        sb.append("<FuncFParam>\n");
        return sb.toString();
    }

    public Ident getIdent() {
        return ident;
    }

    public boolean isArray() {
        return lBrack != null;
    }
}
