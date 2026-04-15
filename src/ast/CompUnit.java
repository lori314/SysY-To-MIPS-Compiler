package ast;

import java.util.ArrayList;

public class CompUnit implements Node {
    private ArrayList<Decl> decl;
    private ArrayList<FuncDef> funcDef;
    private MainFuncDef mainFuncDef;

    public CompUnit(ArrayList<Decl> decl,ArrayList<FuncDef> funcDef,MainFuncDef mainFuncDef) {
        this.decl = decl;
        this.funcDef = funcDef;
        this.mainFuncDef = mainFuncDef;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Decl d : decl) {
            sb.append(d.toString());
        }
        for (FuncDef f : funcDef) {
            sb.append(f.toString());
        }
        sb.append(mainFuncDef.toString());
        sb.append("<CompUnit>\n");
        return sb.toString();
    }

    public ArrayList<Decl> getDecls() {
        return decl;
    }

    public MainFuncDef getMainFuncDef() {
        return mainFuncDef;
    }

    public ArrayList<FuncDef> getFuncDefs() {
        return funcDef;
    }
}
