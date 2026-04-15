package midend.ir.values.instructions;

import midend.ir.types.VoidType;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;

public class ReturnInst extends Instruction {
    // ret i32 %val
    public ReturnInst(Value val, BasicBlock parent) {
        super("", VoidType.voidType, parent);
        addOperand(val);
    }

    // ret void
    public ReturnInst(BasicBlock parent) {
        super("", VoidType.voidType, parent);
    }

    @Override
    public String toString() {
        if (getNumOperands() == 0) {
            return "ret void";
        } else {
            Value val = getOperand(0);
            return "ret " + val.getType() + " " + val.getName();
        }
    }
}
