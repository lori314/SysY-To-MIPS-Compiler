package ast;

import java.util.ArrayList;

public class VarDecl implements Decl {
    private Terminal staticTy;
    private BType bType;
    private VarDef varDef;
    private ArrayList<Terminal> commas;
    private ArrayList<VarDef> varDefs;
    private Terminal semicn;

    public VarDecl(Terminal staticTy, BType bType, VarDef varDef, ArrayList<Terminal> commas, ArrayList<VarDef> varDefs, Terminal semicn) {
        this.staticTy = staticTy;
        this.bType = bType;
        this.varDef = varDef;
        this.commas = commas;
        this.varDefs = varDefs;
        this.semicn = semicn;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (staticTy != null) {
            sb.append(staticTy.toString());
        }
        sb.append(bType.toString());
        sb.append(varDef.toString());
        for (int i = 0; i < commas.size(); i++) {
            sb.append(commas.get(i).toString());
            sb.append(varDefs.get(i).toString());
        }
        sb.append(semicn.toString());
        sb.append("<VarDecl>\n");
        return sb.toString();
    }

    public ArrayList<VarDef> getVarDefs() {
        return varDefs; 
    }

    public VarDef getVarDef() {
        return varDef;
    }

    public Terminal getStaticTy() { return staticTy; }
}
