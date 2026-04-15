package ast;

import frontend.Token;
import frontend.TokenType;

public class Terminal implements Node {
    private final Token token;

    public Terminal(Token token) {
        this.token = token;
    }

    public Token getToken() {
        return token;
    }

    public TokenType getType() {
        return token.getType();
    }

    @Override
    public String toString() {
        return token.getType().name() + " " + token.getValue() + "\n";
    }
}
