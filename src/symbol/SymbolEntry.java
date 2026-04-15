package symbol;

import java.util.ArrayList;

public class SymbolEntry {
    public enum Type { ConstInt,Int,VoidFunc,ConstIntArray,IntArray,IntFunc,StaticInt,StaticIntArray; }

    private String name;
    private Type type;
    private int scopeId;

    private ArrayList<Type> paramTypes;

    // 构造函数 for Variables/Constants
    public SymbolEntry(String name, Type type, int scopeId) {
        this.name = name;
        this.type = type;
        this.scopeId = scopeId;
    }

    // 构造函数 for Functions
    public SymbolEntry(String name, Type returnType, ArrayList<Type> paramTypes, int scopeId) {
        this.name = name;
        this.type = returnType;
        this.paramTypes = paramTypes;
        this.scopeId = scopeId;
    }

    public String getName() { return name; }
    public Type getType() { return type; }
    public int getScopeId() { return scopeId; }
    public ArrayList<Type> getParamTypes() { return paramTypes; }
    public boolean isConst() { return type.equals(Type.ConstInt) || type.equals(Type.ConstIntArray); }

    @Override
    public String toString() {
        return scopeId + " " + name + " " + type;
    }
}