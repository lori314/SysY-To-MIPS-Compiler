package midend.ir.values.instructions;

import midend.ir.types.VoidType;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;

public class StoreInst extends Instruction {
    public StoreInst(Value val, Value ptr, BasicBlock parent) {
        super("", VoidType.voidType, parent); // Store 不产生 Value
        addOperand(val);
        addOperand(ptr);
    }
    
    public Value getValue() { return getOperand(0); }
    public Value getPointer() { return getOperand(1); }

    @Override
    public String toString() {
        Value val = getOperand(0);
        Value ptr = getOperand(1);
        return String.format("store %s %s, %s %s", 
            val.getType(), val.getName(), ptr.getType(), ptr.getName());
    }
}
