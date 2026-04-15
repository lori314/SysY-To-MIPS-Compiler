package ast;

public class UnaryOp implements Node {
    // UnaryOp -> '+' | '-' | '!'
    private final Terminal opToken;

    public UnaryOp(Terminal opToken) {
        this.opToken = opToken;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(opToken.toString());
        sb.append("<UnaryOp>\n");
        return sb.toString();
    }

    public Terminal getOpToken() {
        return opToken;
    }
}