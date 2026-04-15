package midend.ir.values.instructions;

import midend.ir.types.FunctionType;
import midend.ir.types.Type;
import midend.ir.values.BasicBlock;
import midend.ir.values.Function;
import midend.ir.values.Value;

public class CallInst extends Instruction {
    public CallInst(Function func, BasicBlock parent) {
        super(getReturnName(func, parent), ((FunctionType)func.getType()).getReturnType(), parent);
        addOperand(func);
    }
    
    private static String getReturnName(Function func, BasicBlock parent) {
        Type retType = ((FunctionType)func.getType()).getReturnType();
        if (retType.isVoid()) {
            return "";
        } else {
            return "%" + (parent.getParent().getAndUpdateCount());
        }
    }
    
    public void addArgument(Value arg) {
        addOperand(arg);
    }
    
    public Function getFunction() {
        return (Function) getOperand(0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Function func = (Function) getOperand(0);
        Type retType = ((FunctionType)func.getType()).getReturnType();
        
        if (!retType.isVoid()) {
            sb.append(getName()).append(" = ");
        }
        
        sb.append("call ").append(retType).append(" ").append(func.getName()).append("(");
        
        for (int i = 1; i < getNumOperands(); i++) {
            Value arg = getOperand(i);
            sb.append(arg.getType()).append(" ").append(arg.getName());
            if (i < getNumOperands() - 1) sb.append(", ");
        }
        sb.append(")");
        
        return sb.toString();
    }
}
