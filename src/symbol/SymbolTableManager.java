package symbol;

import java.util.ArrayList;
import java.util.Stack;

public class SymbolTableManager {
    private Stack<Scope> scopeStack = new Stack<>();
    private ArrayList<Scope> allScopes = new ArrayList<>();
    private int nextScopeId = 1;

    public SymbolTableManager() {
        enterScope();

        initializeBuiltinFunctions();
    }

    private void initializeBuiltinFunctions() {
        Scope globalScope = getCurrentScope();
        SymbolEntry getintEntry = new SymbolEntry(
                "getint",
                SymbolEntry.Type.IntFunc,
                new ArrayList<>(),
                globalScope.getId()
        );
        globalScope.addSymbol(getintEntry);
    }

    public void enterScope() {
        Scope parent = scopeStack.isEmpty() ? null : scopeStack.peek();
        Scope newScope = new Scope(nextScopeId++, parent);
        scopeStack.push(newScope);
        allScopes.add(newScope);
    }

    public void exitScope() {
        if (!scopeStack.isEmpty()) {
            scopeStack.pop();
        }
    }

    public boolean addSymbolToParentScope(SymbolEntry symbol) {
        if (scopeStack.size() < 2) {
            return false;
        }
        Scope parentScope = scopeStack.get(scopeStack.size() - 2);
        return parentScope.addSymbol(symbol);
    }

    public Scope getCurrentScope() {
        return scopeStack.peek();
    }

    public boolean addSymbol(SymbolEntry symbol) {
        return getCurrentScope().addSymbol(symbol);
    }

    public SymbolEntry findSymbol(String name) {
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            SymbolEntry entry = scopeStack.get(i).findSymbolLocally(name);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    public Scope getParentScope() {
        return scopeStack.isEmpty() ? null : scopeStack.get(scopeStack.size() - 1).getParent();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Scope scope : allScopes) {
            stringBuilder.append(scope.toString());
        }
        return stringBuilder.toString();
    }
}
