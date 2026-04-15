package midend.ir.types;

public class ArrayType extends Type {
    private final Type elementType;
    private final int numElements;
    public ArrayType(Type elementType, int numElements) {
        this.elementType = elementType;
        this.numElements = numElements;
    }
    public Type getElementType() { return elementType; }
    public int getNumElements() { return numElements; }
    @Override public String toString() { return "[" + numElements + " x " + elementType + "]"; }
}
