package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

import java.net.IDN;

public class LVal implements Node {
    private final Ident ident;
    private final Terminal lBrack;
    private final Exp exp;
    private final Terminal rBrack;

    public LVal(Ident ident, Terminal lBrack, Exp exp, Terminal rBrack) {
        this.ident = ident;
        this.lBrack = lBrack;
        this.exp = exp;
        this.rBrack = rBrack;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        SymbolEntry entry = manager.findSymbol(ident.getName());
        if (entry == null) {
            return null;
        }
        boolean isArrayAccess = (this.lBrack != null);
        if (isArrayAccess) {
            return SymbolEntry.Type.Int;
        } else {
            return entry.getType();
        }
    }

    public Ident getIdent() {
        return ident;
    }

    public boolean isArrayAccess() {
        return lBrack != null;
    }

    public Exp getExp() { return exp; }

    public Terminal getLBrack() { return lBrack; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ident.toString());
        if (lBrack != null) {
            sb.append(lBrack.toString());
            sb.append(exp.toString());
            sb.append(rBrack.toString());
        }
        sb.append("<LVal>\n");
        return sb.toString();
    }
}