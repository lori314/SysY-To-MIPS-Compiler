package midend.ir.values;

import midend.ir.types.LabelType;
import midend.ir.values.instructions.Instruction;
import java.util.LinkedList;
import java.util.ArrayList;

public class BasicBlock extends Value {
    private Function parent;
    private final LinkedList<Instruction> instructions = new LinkedList<>();
    private final ArrayList<BasicBlock> predecessors = new ArrayList<>();
    private final ArrayList<BasicBlock> successors = new ArrayList<>();

    public BasicBlock(String name, Function parent) {
        super(name, LabelType.labelType);
        this.parent = parent;
        parent.addBasicBlock(this); // 自动挂载
    }

    public void addInstruction(Instruction inst) {
        instructions.add(inst);
    }
    
    public void removeInstruction(Instruction inst) {
        instructions.remove(inst);
    }

    /**
     * 在终结指令之前插入指令
     * 用于循环不变式外提等优化
     */
    public void insertBeforeTerminator(Instruction inst) {
        if (instructions.isEmpty()) {
            instructions.add(inst);
        } else {
            Instruction last = instructions.getLast();
            if (last.isTerminator()) {
                instructions.add(instructions.size() - 1, inst);
            } else {
                instructions.add(inst);
            }
        }
        inst.setParent(this);
    }
    
    public LinkedList<Instruction> getInstructions() { return instructions; }
    public Function getParent() { return parent; }
    
    public ArrayList<BasicBlock> getPredecessors() { return predecessors; }
    public ArrayList<BasicBlock> getSuccessors() { return successors; }
    
    // 清空图信息（每次重新构建CFG时调用）
    public void cleanSuccessors() {
        predecessors.clear();
        successors.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name.substring(1)).append(":\n"); // 去掉 name 中的 % 或 label 前缀
        for (Instruction inst : instructions) {
            sb.append("  ").append(inst.toString()).append("\n");
        }
        return sb.toString();
    }
}
