package midend.ir.types;

public class PointerType extends Type {
    private final Type pointeeType;
    public PointerType(Type pointeeType) { this.pointeeType = pointeeType; }
    public Type getPointeeType() { return pointeeType; }
    @Override public String toString() { return pointeeType.toString() + "*"; }
}
