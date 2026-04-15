package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class LAndExp implements Node {
    private final LAndExp left;
    private final Terminal op;
    private final EqExp right;

    public LAndExp(EqExp eqExp) {
        this.left = null;
        this.op = null;
        this.right = eqExp;
    }

    public LAndExp(LAndExp left, Terminal op, EqExp right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        if (left == null) {
            return right.inferType(manager); // 类型由 EqExp 決定
        } else {
            SymbolEntry.Type leftType = left.inferType(manager);
            SymbolEntry.Type rightType = right.inferType(manager);
            if (leftType == null || rightType == null) {
                return null;
            }
            return SymbolEntry.Type.Int;
        }
    }

    public LAndExp getLeft() { return left; }
    public Terminal getOp() { return op; }
    public EqExp getRight() { return right; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (left != null) {
            sb.append(left.toString());
            sb.append(op.toString());
        }
        sb.append(right.toString());
        sb.append("<LAndExp>\n");
        return sb.toString();
    }
}