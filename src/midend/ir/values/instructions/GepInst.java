package midend.ir.values.instructions;

import midend.ir.types.ArrayType;
import midend.ir.types.PointerType;
import midend.ir.types.Type;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;

public class GepInst extends Instruction {
    public GepInst(Value ptr, BasicBlock parent) {
        super("%" + (parent.getParent().getAndUpdateCount()), 
              calculateResultType(ptr.getType()), parent);
        addOperand(ptr);
    }
    
    private static Type calculateResultType(Type ptrType) {
        if (ptrType instanceof PointerType) {
            Type pointee = ((PointerType)ptrType).getPointeeType();
            if (pointee instanceof ArrayType) {
                // 数组指针降维
                return new PointerType(((ArrayType)pointee).getElementType());
            }
            return new PointerType(pointee);
        }
        return ptrType;
    }
    
    public void addIndex(Value index) {
        addOperand(index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Value ptr = getOperand(0);
        Type ptrType = ptr.getType();
        
        sb.append(getName()).append(" = getelementptr ");
        
        if (ptrType instanceof PointerType) {
            Type pointee = ((PointerType)ptrType).getPointeeType();
            sb.append(pointee).append(", ");
        }
        
        sb.append(ptrType).append(" ").append(ptr.getName());
        
        for (int i = 1; i < getNumOperands(); i++) {
            Value idx = getOperand(i);
            sb.append(", ").append(idx.getType()).append(" ").append(idx.getName());
        }
        
        return sb.toString();
    }
}
