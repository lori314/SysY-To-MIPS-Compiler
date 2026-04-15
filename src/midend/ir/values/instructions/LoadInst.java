package midend.ir.values.instructions;

import midend.ir.types.PointerType;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;

public class LoadInst extends Instruction {
    public LoadInst(Value ptr, BasicBlock parent) {
        // 返回类型是 ptr指向的类型
        super("%" + (parent.getParent().getAndUpdateCount()), 
              ((PointerType) ptr.getType()).getPointeeType(), parent);
        addOperand(ptr);
    }
    
    public Value getPointer() {
        return getOperand(0);
    }

    @Override
    public String toString() {
        Value ptr = getOperand(0);
        return String.format("%s = load %s, %s %s", 
            getName(), getType().toString(), ptr.getType().toString(), ptr.getName());
    }
}
