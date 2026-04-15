package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class AddExp implements Node {
    private final AddExp left;
    private final Terminal op;
    private final MulExp right;

    public AddExp(MulExp mulExp) {
        this.left = null;
        this.op = null;
        this.right = mulExp;
    }

    public AddExp(AddExp left, Terminal op, MulExp right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    // 在 ast/AddExp.java 中
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (left != null) {
            sb.append(left.toString());
            sb.append(op.toString());
        }
        sb.append(right.toString());
        sb.append("<AddExp>\n");
        return sb.toString();
    }

    public AddExp getLeft() {
        return left;
    }

    public MulExp getRight() {
        return right;
    }

    public Terminal getOp() {
        return op;
    }
}