package midend.ir.types;

public abstract class Type {
    public boolean isVoid() { return this instanceof VoidType; }
    public boolean isInt() { return this instanceof IntType; } // i32 or i1
    public boolean isPointer() { return this instanceof PointerType; }
    public boolean isArray() { return this instanceof ArrayType; }
    public boolean isFunction() { return this instanceof FunctionType; }
    public boolean isLabel() { return this instanceof LabelType; }

    @Override
    public abstract String toString();
}
