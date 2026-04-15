package ast;

import java.util.ArrayList;

public class ConstInitVal implements Node {
    private final boolean isArray;
    private ConstExp constExp;
    private Terminal lBrace;
    private ArrayList<ConstExp> constExps;
    private ArrayList<Terminal> commas;
    private Terminal rBrace;

    public ConstInitVal(boolean isArray, ConstExp constExp) {
        this.isArray = isArray;
        this.constExp = constExp;
    }

    public ConstInitVal(boolean isArray, Terminal lBrace, ArrayList<ConstExp> constExps, ArrayList<Terminal> commas, Terminal rBrace) {
        this.isArray = isArray;
        this.lBrace = lBrace;
        this.constExps = constExps;
        this.commas = commas;
        this.rBrace = rBrace;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!isArray) {
            sb.append(constExp.toString());
        } else {
            sb.append(lBrace.toString());
            if (constExps != null && !constExps.isEmpty()) {
                sb.append(constExps.get(0).toString());
                for (int i = 1; i < constExps.size(); i++) {
                    sb.append(commas.get(i - 1).toString());
                    sb.append(constExps.get(i).toString());
                }
            }
            sb.append(rBrace.toString());
        }
        sb.append("<ConstInitVal>\n");
        return sb.toString();
    }

    public ConstExp getConstExp() {
        return constExp;
    }

    // Added for codegen: expose list constant initializers for arrays
    public ArrayList<ConstExp> getConstExps() {
        return constExps;
    }

    public boolean isArray() { return isArray; }
}
