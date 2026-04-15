package midend.ir.values.instructions;

import midend.ir.types.VoidType;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;

public class BranchInst extends Instruction {
    // 构造条件跳转: br i1 %cond, label %true, label %false
    public BranchInst(Value cond, BasicBlock ifTrue, BasicBlock ifFalse, BasicBlock parent) {
        super("", VoidType.voidType, parent);
        addOperand(cond);
        addOperand(ifTrue);
        addOperand(ifFalse);
    }

    // 构造无条件跳转: br label %dest
    public BranchInst(BasicBlock dest, BasicBlock parent) {
        super("", VoidType.voidType, parent);
        addOperand(dest);
    }
    
    public boolean isConditional() {
        return getNumOperands() == 3;
    }
    
    public Value getCondition() {
        return isConditional() ? getOperand(0) : null;
    }
    
    public BasicBlock getTrueBlock() {
        return isConditional() ? (BasicBlock) getOperand(1) : null;
    }
    
    public BasicBlock getFalseBlock() {
        return isConditional() ? (BasicBlock) getOperand(2) : null;
    }
    
    public BasicBlock getTarget() {
        return isConditional() ? null : (BasicBlock) getOperand(0);
    }

    @Override
    public String toString() {
        if (isConditional()) {
            return String.format("br i1 %s, label %s, label %s", 
                getOperand(0).getName(), getOperand(1).getName(), getOperand(2).getName());
        } else {
            return String.format("br label %s", getOperand(0).getName());
        }
    }
}
