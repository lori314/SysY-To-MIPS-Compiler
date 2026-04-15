package ast;

import symbol.SymbolEntry;
import symbol.SymbolTableManager;

public class MulExp implements Node {
    private final MulExp left;
    private final Terminal op;
    private final UnaryExp right;

    public MulExp(UnaryExp unaryExp) {
        this.left = null;
        this.op = null;
        this.right = unaryExp;
    }

    public MulExp(MulExp left, Terminal op, UnaryExp right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public SymbolEntry.Type inferType(SymbolTableManager manager) {
        if (left == null) {
            return right.inferType(manager); // 类型由 UnaryExp 决定
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
        sb.append("<MulExp>\n");
        return sb.toString();
    }

    public MulExp getLeft() {
        return left;
    }

    public UnaryExp getRight() {
        return right;
    }

    public Terminal getOp() {
        return op;
    }
}