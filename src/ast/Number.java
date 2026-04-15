package ast;

public class Number implements Node {
    private final Terminal intConst;

    public Number(Terminal intConst) {
        this.intConst = intConst;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(intConst.toString());
        sb.append("<Number>\n");
        return sb.toString();
    }

    public Terminal getIntConst() {
        return intConst;
    }
}
