package ast;

public class ConstExp implements Node {
    // ConstExp -> AddExp
    private final AddExp addExp;

    public ConstExp(AddExp addExp) {
        this.addExp = addExp;
    }

    @Override
    public String toString() {
        return addExp.toString() + "<ConstExp>\n";
    }

    public AddExp getAddExp() {
        return addExp;
    }
}