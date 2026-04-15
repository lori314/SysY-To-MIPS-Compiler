package midend.ir.values.instructions;

import midend.ir.types.IntType;
import midend.ir.types.Type;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;

public class ZextInst extends Instruction {
    private final Type targetType;
    
    public ZextInst(Value val, Type targetType, BasicBlock parent) {
        super("%" + (parent.getParent().getAndUpdateCount()), targetType, parent);
        this.targetType = targetType;
        addOperand(val);
    }

    @Override
    public String toString() {
        Value val = getOperand(0);
        return String.format("%s = zext %s %s to %s", 
            getName(), val.getType(), val.getName(), targetType);
    }
}
