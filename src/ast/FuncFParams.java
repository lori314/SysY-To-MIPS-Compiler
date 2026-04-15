package ast;

import java.util.ArrayList;

public class FuncFParams implements Node {
    private FuncFParam funcFParam;
    private ArrayList<FuncFParam> funcFParams;
    private ArrayList<Terminal> commas;

    public FuncFParams(FuncFParam funcFParam, ArrayList<FuncFParam> funcFParams, ArrayList<Terminal> commas) {
        this.funcFParam = funcFParam;
        this.funcFParams = funcFParams;
        this.commas = commas;
    }

    public ArrayList<FuncFParam> getAllParams() {
        ArrayList<FuncFParam> allParams = new ArrayList<>();
        allParams.add(funcFParam);
        allParams.addAll(funcFParams);
        return allParams;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(funcFParam.toString());
        for (int i = 0; i < funcFParams.size(); i++) {
            sb.append(commas.get(i).toString());
            sb.append(funcFParams.get(i).toString());
        }
        sb.append("<FuncFParams>\n");
        return sb.toString();
    }
}
