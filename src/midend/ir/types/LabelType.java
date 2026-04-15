package midend.ir.types;

public class LabelType extends Type {
    public static final LabelType labelType = new LabelType();
    private LabelType() {}
    @Override public String toString() { return "label"; }
}
