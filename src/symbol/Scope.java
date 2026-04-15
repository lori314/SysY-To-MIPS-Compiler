package symbol;

import java.util.LinkedHashMap; // 关键：保持插入顺序
import java.util.Map;

public class Scope {
    private int id;
    private Scope parent;
    private Map<String, SymbolEntry> symbols = new LinkedHashMap<>();

    public Scope(int id, Scope parent) {
        this.id = id;
        this.parent = parent;
    }

    public boolean addSymbol(SymbolEntry symbol) {
        if (symbols.containsKey(symbol.getName())) {
            return false;
        }
        symbols.put(symbol.getName(), symbol);
        return true;
    }

    public SymbolEntry findSymbolLocally(String name) {
        return symbols.get(name);
    }

    public int getId() { return id; }
    public Scope getParent() { return parent; }
    public Map<String, SymbolEntry> getSymbols() { return symbols; }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (Map.Entry<String, SymbolEntry> entry : symbols.entrySet()) {
            SymbolEntry symbolEntry = entry.getValue();
            if (symbolEntry.getName().equals("getint")) {
                continue;
            }
            str.append(symbolEntry.toString()).append('\n');
        }
        return str.toString();
    }
}
