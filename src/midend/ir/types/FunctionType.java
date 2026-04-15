package midend.ir.types;
import java.util.ArrayList;

public class FunctionType extends Type {
    private final Type returnType;
    private final ArrayList<Type> paramTypes;

    public FunctionType(Type returnType, ArrayList<Type> paramTypes) {
        this.returnType = returnType;
        this.paramTypes = paramTypes;
    }
    public Type getReturnType() { return returnType; }
    public ArrayList<Type> getParamTypes() { return paramTypes; }
    @Override public String toString() { return returnType.toString(); } // 简化打印
}
