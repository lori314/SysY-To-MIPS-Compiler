package midend.ir.values.instructions;

import midend.ir.types.PointerType;
import midend.ir.types.Type;
import midend.ir.values.BasicBlock;

public class AllocaInst extends Instruction {
    private final Type allocatedType; // 分配的是什么类型 (比如 i32)

    public AllocaInst(Type allocatedType, BasicBlock parent) {
        // 返回类型是 allocatedType* (指针)
        super("%" + (parent.getParent().getAndUpdateCount()), new PointerType(allocatedType), parent);
        this.allocatedType = allocatedType;
    }
    
    public Type getAllocatedType() { return allocatedType; }

    @Override
    public String toString() {
        return String.format("%s = alloca %s", getName(), allocatedType.toString());
    }
}
