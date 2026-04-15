package ast;

import frontend.TokenType;

public class FuncType implements Node {
    private Terminal type;

    public FuncType(Terminal type) {
        this.type = type;
    }

    public boolean isVoid() {
        return type.getToken().getType() == TokenType.VOIDTK;
    }

    @Override
    public String toString() {
        return type.toString() + "<FuncType>\n";
    }

}
