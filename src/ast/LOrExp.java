package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class LOrExp implements Node {
    private final LOrExp left;
    private final Terminal op;
    private final LAndExp right;

    public LOrExp(LAndExp lAndExp) {
        this.left = null;
        this.op = null;
        this.right = lAndExp;
    }

    public LOrExp(LOrExp left, Terminal op, LAndExp right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        if (left == null) {
            return right.inferType(manager); // 类型由 LAndExp 決定
        } else {
            SymbolEntry.Type leftType = left.inferType(manager);
            SymbolEntry.Type rightType = right.inferType(manager);
            if (leftType == null || rightType == null) {
                return null;
            }
            return SymbolEntry.Type.Int;
        }
    }

    public LOrExp getLeft() { return left; }
    public Terminal getOp() { return op; }
    public LAndExp getRight() { return right; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (left != null) {
            sb.append(left.toString());
            sb.append(op.toString());
        }
        sb.append(right.toString());
        sb.append("<LOrExp>\n");
        return sb.toString();
    }
}