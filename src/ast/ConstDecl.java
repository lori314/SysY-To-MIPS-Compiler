package ast;

import java.util.ArrayList;

public class ConstDecl implements Decl {
    private Terminal constTy;
    private BType bType;
    private ConstDef constDef;
    private ArrayList<Terminal> commas;
    private ArrayList<ConstDef> constDefs;
    private Terminal semicn;

    public ConstDecl(Terminal constTy,BType bType,ConstDef constDef,ArrayList<Terminal> commas,ArrayList<ConstDef> constDefs,Terminal semicn) {
        this.constTy = constTy;
        this.bType = bType;
        this.constDef = constDef;
        this.commas = commas;
        this.constDefs = constDefs;
        this.semicn = semicn;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(constTy.toString());
        sb.append(bType.toString());
        sb.append(constDef.toString());
        for (int i = 0; i < commas.size(); i++) {
            sb.append(commas.get(i).toString());
            sb.append(constDefs.get(i).toString());
        }
        sb.append(semicn.toString());
        sb.append("<ConstDecl>\n");
        return sb.toString();
    }

    public ArrayList<ConstDef> getConstDefs() {
        return constDefs;
    }

    public ConstDef getConstDef() {
        return constDef;
    }
}
