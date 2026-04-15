package ast;

public class ConstDef implements Node {
    private Ident ident;
    private Terminal lBrack;
    private ConstExp constExp;
    private Terminal rBrack;
    private Terminal eql;
    private ConstInitVal constInitVal;

    public ConstDef(Ident ident, Terminal lBrack, ConstExp constExp, Terminal rBrack, Terminal eql, ConstInitVal constInitVal) {
        this.ident = ident;
        this.lBrack = lBrack;
        this.constExp = constExp;
        this.rBrack = rBrack;
        this.eql = eql;
        this.constInitVal = constInitVal;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ident.toString());
        if (lBrack != null) {
            sb.append(lBrack.toString());
            sb.append(constExp.toString());
            sb.append(rBrack.toString());
        }
        sb.append(eql.toString());
        sb.append(constInitVal.toString());
        sb.append("<ConstDef>\n");
        return sb.toString();
    }

    public Ident getIdent() {
        return ident;
    }

    public ConstInitVal getConstInitVal() {
        return constInitVal;
    }

    // Array helpers
    public boolean isArray() {
        return lBrack != null;
    }

    public ConstExp getConstExp() {
        return constExp;
    }

    public Terminal getLBrack() { return lBrack; }
}
