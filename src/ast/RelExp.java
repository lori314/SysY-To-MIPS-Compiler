package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class RelExp implements Node {
    private final RelExp left;
    private final Terminal op;
    private final AddExp right;

    public RelExp(AddExp addExp) {
        this.left = null;
        this.op = null;
        this.right = addExp;
    }

    public RelExp(RelExp left, Terminal op, AddExp right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        if (left == null) {
            return right.inferType(manager);
        } else {
            SymbolEntry.Type leftType = left.inferType(manager);
            SymbolEntry.Type rightType = right.inferType(manager);
            if (leftType == null || rightType == null) {
                return null;
            }
            return SymbolEntry.Type.Int;
        }
    }

    public RelExp getLeft() { return left; }
    public Terminal getOp() { return op; }
    public AddExp getRight() { return right; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (left != null) {
            sb.append(left.toString());
            sb.append(op.toString());
        }
        sb.append(right.toString());
        sb.append("<RelExp>\n");
        return sb.toString();
    }
}