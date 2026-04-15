package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class Cond implements Node {
    // Cond -> LOrExp
    private final LOrExp lOrExp;

    public Cond(LOrExp lOrExp) {
        this.lOrExp = lOrExp;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        return lOrExp.inferType(manager);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(lOrExp.toString());
        sb.append("<Cond>\n");
        return sb.toString();
    }

    public LOrExp getLOrExp() { return lOrExp; }
}
