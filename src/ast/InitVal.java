package ast;

import java.util.ArrayList;

public class InitVal implements Node {
    private final boolean isArray;
    private Exp exp;
    private Terminal lBrace;
    private ArrayList<Exp> exps;
    private ArrayList<Terminal> commas;
    private Terminal rBrace;

    public InitVal(boolean isArray, Exp exp) {
        this.isArray = isArray;
        this.exp = exp;
    }

    public InitVal(boolean isArray, Terminal lBrace, ArrayList<Exp> exps, ArrayList<Terminal> commas, Terminal rBrace) {
        this.isArray = isArray;
        this.lBrace = lBrace;
        this.exps = exps;
        this.commas = commas;
        this.rBrace = rBrace;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!isArray) {
            sb.append(exp.toString());
        } else {
            sb.append(lBrace.toString());
            if (exps != null && !exps.isEmpty()) {
                sb.append(exps.get(0).toString());
                for (int i = 1; i < exps.size(); i++) {
                    sb.append(commas.get(i - 1).toString());
                    sb.append(exps.get(i).toString());
                }
            }
            sb.append(rBrace.toString());
        }
        sb.append("<InitVal>\n");
        return sb.toString();
    }

    public Exp getExp() {
        return exp;
    }

    // Added for codegen: expose list initializers when isArray=true
    public ArrayList<Exp> getExps() {
        return exps;
    }

    public boolean isArray() { return isArray; }
}
