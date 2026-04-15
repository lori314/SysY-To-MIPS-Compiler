package ast;

import frontend.Token;

public class Ident implements Node {
    private final Terminal identifierToken;

    public Ident(Terminal identifierToken) {
        this.identifierToken = identifierToken;
    }

    public Token getIdentifierToken() {
        return identifierToken.getToken();
    }

    public String getName() {
        return identifierToken.getToken().getValue();
    }

    @Override
    public String toString() {
        return identifierToken.toString();
    }
}