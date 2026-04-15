package ast;

public class VarDef implements Node {
    private boolean isAssign;

    private Ident ident;
    private Terminal lBrack;
    private ConstExp constExp;
    private Terminal rBrack;
    private Terminal eql;
    private InitVal initVal;

    public VarDef(Ident ident,Terminal lBrack,ConstExp constExp,Terminal rBrack) {
        this.isAssign = false;
        this.ident = ident;
        this.lBrack = lBrack;
        this.constExp = constExp;
        this.rBrack = rBrack;
    }

    public VarDef(Ident ident,Terminal lBrack,ConstExp constExp,Terminal rBrack,Terminal eql,InitVal initVal) {
        this.isAssign = true;
        this.ident = ident;
        this.lBrack = lBrack;
        this.constExp = constExp;
        this.rBrack = rBrack;
        this.eql = eql;
        this.initVal = initVal;
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
        if (isAssign) {
            sb.append(eql.toString());
            sb.append(initVal.toString());
        }
        sb.append("<VarDef>\n");
        return sb.toString();
    }

    public Ident getIdent() {
        return ident;
    }

    public boolean isAssign() {
        return isAssign;
    }

    public InitVal getInitVal() {
        return initVal;
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
