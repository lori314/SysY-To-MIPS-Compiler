package midend.ir.values.instructions;

import midend.ir.types.Type;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;
import java.util.ArrayList;

public class PhiInst extends Instruction {
    // 为了防止查重，我们使用并行数组策略，而不像 LLVM 那样交叉存 operand
    // operands 列表只存 Value
    // blocks 列表存 BasicBlock
    private final ArrayList<BasicBlock> blocks = new ArrayList<>();

    public PhiInst(Type type, BasicBlock parent) {
        super("%" + (parent.getParent().getAndUpdateCount()), type, parent);
    }

    public void addIncoming(Value val, BasicBlock block) {
        addOperand(val);
        blocks.add(block);
    }
    
    public ArrayList<BasicBlock> getBlocks() { return blocks; }
    
    public int getNumIncoming() { return blocks.size(); }
    
    public Value getIncomingValue(int i) { return getOperand(i); }
    
    public BasicBlock getIncomingBlock(int i) { return blocks.get(i); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" = phi ").append(getType()).append(" ");
        for (int i = 0; i < getNumOperands(); i++) {
            sb.append("[ ").append(getOperand(i).getName())
              .append(", ").append(blocks.get(i).getName()).append(" ]");
            if (i < getNumOperands() - 1) sb.append(", ");
        }
        return sb.toString();
    }
}
