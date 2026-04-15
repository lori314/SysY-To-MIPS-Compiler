package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class Exp implements Node {
    private final AddExp addExp;

    public Exp(AddExp addExp) {
        this.addExp = addExp;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        return addExp.inferType(manager);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(addExp.toString());
        sb.append("<Exp>\n");
        return sb.toString();
    }

    public AddExp getAddExp() {
        return addExp;
    }
}
