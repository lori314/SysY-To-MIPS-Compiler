package midend.ir.types;

public class IntType extends Type {
    private final int bits;
    public static final IntType I32 = new IntType(32);
    public static final IntType I1 = new IntType(1);
    public static final IntType I8 = new IntType(8);

    public IntType(int bits) { this.bits = bits; }
    public int getBits() { return bits; }
    @Override public String toString() { return "i" + bits; }
}
