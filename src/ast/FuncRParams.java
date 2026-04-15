package ast;

import symbol.SymbolEntry;

import java.util.ArrayList;

public class FuncRParams implements Node {
    // FuncRParams -> Exp { ',' Exp }
    private final Exp firstParam;
    private final ArrayList<Terminal> commas;
    private final ArrayList<Exp> otherParams;

    public FuncRParams(Exp firstParam, ArrayList<Terminal> commas, ArrayList<Exp> otherParams) {
        this.firstParam = firstParam;
        this.commas = commas;
        this.otherParams = otherParams;
    }

    public int getNumParams() {
        return commas.size() + 1;
    }

    public ArrayList<Exp> getParamExps() {
        ArrayList<Exp> allParams = new ArrayList<>();
        if (firstParam instanceof Exp) {
            allParams.add((Exp) firstParam);
        }
        allParams.addAll(otherParams);
        return allParams;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(firstParam.toString());
        for (int i = 0; i < commas.size(); i++) {
            sb.append(commas.get(i).toString());
            sb.append(otherParams.get(i).toString());
        }
        sb.append("<FuncRParams>\n");
        return sb.toString();
    }
}