package midend.ir.values.instructions;

import midend.ir.types.Type;
import midend.ir.values.BasicBlock;
import midend.ir.values.User;

public abstract class Instruction extends User {
    private BasicBlock parent; // 指令所属的基本块

    public Instruction(String name, Type type, BasicBlock parent) {
        super(name, type);
        this.parent = parent;
        // 构造时自动挂载到基本块末尾
        if (parent != null) {
            parent.addInstruction(this);
        }
    }

    public BasicBlock getParent() { return parent; }
    
    public void setParent(BasicBlock parent) { this.parent = parent; }

    // 从基本块中移除自己（用于死代码删除）
    public void remove() {
        if (parent != null) {
            parent.getInstructions().remove(this);
            parent = null;
        }
        // 移除对操作数的引用
        for (midend.ir.values.Value op : operands) {
            if (op != null) {
                op.removeUse(this);
            }
        }
        this.operands.clear(); 
    }
    
    // 是否是终结指令（跳转/返回）
    public boolean isTerminator() {
        return this instanceof BranchInst || this instanceof ReturnInst;
    }
}
