package midend.ir.values;

import midend.ir.types.FunctionType;
import midend.ir.types.Type;
import java.util.ArrayList;
import java.util.LinkedList;

public class Function extends Value {
    private final ArrayList<Argument> arguments = new ArrayList<>();
    // 使用 LinkedList 方便插入/删除基本块
    private final LinkedList<BasicBlock> basicBlocks = new LinkedList<>(); 
    private boolean isBuiltin = false;
    private int valCounter = 0; // 为临时变量生成计数器

    public Function(String name, FunctionType type, boolean isBuiltin) {
        super(name, type);
        this.isBuiltin = isBuiltin;
    }
    
    public int getAndUpdateCount() { return valCounter++; }

    public void addBasicBlock(BasicBlock bb) {
        basicBlocks.add(bb);
    }
    
    public void addArgument(Argument arg) {
        arguments.add(arg);
    }
    
    public LinkedList<BasicBlock> getBasicBlocks() { return basicBlocks; }
    public ArrayList<Argument> getArguments() { return arguments; }
    public boolean isBuiltin() { return isBuiltin; }
    
    @Override
    public String toString() {
        if (isBuiltin) return ""; // 声明已在 Module 头部处理
        StringBuilder sb = new StringBuilder();
        sb.append("define ").append(((FunctionType)type).getReturnType()).append(" ").append(name).append("(");
        for (int i = 0; i < arguments.size(); i++) {
            sb.append(arguments.get(i));
            if (i < arguments.size() - 1) sb.append(", ");
        }
        sb.append(") {\n");
        for (BasicBlock bb : basicBlocks) {
            sb.append(bb.toString());
        }
        sb.append("}\n");
        return sb.toString();
    }
}
