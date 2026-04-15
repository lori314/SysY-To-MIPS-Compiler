package ast;

import java.util.ArrayList;

public class ForStmt implements Node {
    // 第一个必须存在的赋值部分
    private final LVal firstLVal;
    private final Terminal firstAssign;
    private final Exp firstExp;

    // 后面可选的、由逗号分隔的部分
    private final ArrayList<Terminal> commas;
    private final ArrayList<LVal> otherLVals;
    private final ArrayList<Terminal> otherAssigns;
    private final ArrayList<Exp> otherExps;

    public ForStmt(LVal firstLVal, Terminal firstAssign, Exp firstExp,
                   ArrayList<Terminal> commas, ArrayList<LVal> otherLVals,
                   ArrayList<Terminal> otherAssigns, ArrayList<Exp> otherExps) {
        this.firstLVal = firstLVal;
        this.firstAssign = firstAssign;
        this.firstExp = firstExp;
        this.commas = commas;
        this.otherLVals = otherLVals;
        this.otherAssigns = otherAssigns;
        this.otherExps = otherExps;
    }

    // --- Getters for code generation ---
    public LVal getFirstLVal() {
        return firstLVal;
    }

    public Exp getFirstExp() {
        return firstExp;
    }

    public ArrayList<LVal> getOtherLVals() {
        return otherLVals;
    }

    public ArrayList<Exp> getOtherExps() {
        return otherExps;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // 打印第一个赋值语句
        sb.append(firstLVal.toString());
        sb.append(firstAssign.toString());
        sb.append(firstExp.toString());

        // 循环打印后续的赋值语句
        for (int i = 0; i < commas.size(); i++) {
            sb.append(commas.get(i).toString());
            sb.append(otherLVals.get(i).toString());
            sb.append(otherAssigns.get(i).toString());
            sb.append(otherExps.get(i).toString());
        }

        sb.append("<ForStmt>\n");
        return sb.toString();
    }
}
