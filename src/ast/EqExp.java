package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class EqExp implements Node {
    private final EqExp left;
    private final Terminal op;
    private final RelExp right;

    public EqExp(RelExp relExp) {
        this.left = null;
        this.op = null;
        this.right = relExp;
    }

    public EqExp(EqExp left, Terminal op, RelExp right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        if (left == null) {
            return right.inferType(manager); // 类型由 RelExp 决定
        } else {
            SymbolEntry.Type leftType = left.inferType(manager);
            SymbolEntry.Type rightType = right.inferType(manager);
            if (leftType == null || rightType == null) {
                return null;
            }
            return SymbolEntry.Type.Int;
        }
    }

    public EqExp getLeft() { return left; }
    public Terminal getOp() { return op; }
    public RelExp getRight() { return right; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (left != null) {
            sb.append(left.toString());
            sb.append(op.toString());
            sb.append(right.toString());
        } else {
            sb.append(right.toString());
        }
        sb.append("<EqExp>\n");
        return sb.toString();
    }
}