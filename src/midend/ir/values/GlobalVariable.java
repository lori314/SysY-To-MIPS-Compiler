package midend.ir.values;

import midend.ir.types.Type;
import midend.ir.types.PointerType;

public class GlobalVariable extends User {
    private Value initializer;
    private boolean isConstant;

    public GlobalVariable(String name, Type type, boolean isConstant) {
        super(name, type);
        this.isConstant = isConstant;
    }

    public void setInitializer(Value init) {
        this.initializer = init;
    }

    public Value getInitializer() {
        return initializer;
    }

    public boolean isConstant() {
        return isConstant;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" = ");
        sb.append(isConstant ? "constant " : "global ");
        Type elemType = ((PointerType)type).getPointeeType();
        sb.append(elemType.toString());
        if (initializer != null) {
            sb.append(" ").append(initializer.toString());
        } else {
            sb.append(" zeroinitializer");
        }
        return sb.toString();
    }
}
