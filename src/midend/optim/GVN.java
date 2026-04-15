package midend.optim;

import midend.ir.values.*;
import midend.ir.values.constants.ConstInt;
import midend.ir.values.instructions.*;

import java.util.*;

/**
 * GVN (Global Value Numbering) - 全局值编号优化
 * 
 * 核心思想：为每个表达式分配一个唯一编号，相同表达式使用相同编号
 * 如果两个表达式有相同的编号，后者可以被前者替换
 * 
 * 实现策略：
 * 1. 使用支配树的先序遍历确保定义先于使用
 * 2. 使用哈希表记录已见过的表达式
 * 3. 对于二元运算和 GEP 指令进行值编号
 */
public class GVN {
    
    // 表达式 -> 代表值 的映射
    private final Map<String, Value> valueNumberTable = new HashMap<>();
    
    // 内存位置 -> Load指令 的映射（用于消除冗余Load）
    private final Map<String, LoadInst> memoryTable = new HashMap<>();
    
    // 支配树分析
    private DomAnalysis domAnalysis;
    
    // 要删除的指令列表
    private final Set<Instruction> toRemove = new HashSet<>();
    
    public void run(midend.ir.values.Module module) {
        // 先运行支配树分析
        domAnalysis = new DomAnalysis();
        domAnalysis.run(module);
        
        for (Function func : module.getFunctions()) {
            if (func.isBuiltin() || func.getBasicBlocks().isEmpty()) continue;
            runOnFunction(func);
        }
    }
    
    private void runOnFunction(Function func) {
        valueNumberTable.clear();
        memoryTable.clear();
        toRemove.clear();
        
        // 获取先序遍历顺序（基于支配树）
        List<BasicBlock> preorder = getPreorder(func);
        
        // 对每个基本块进行值编号
        for (BasicBlock bb : preorder) {
            processBlock(bb);
        }
        
        // 删除被替换的指令
        for (Instruction inst : toRemove) {
            inst.getParent().removeInstruction(inst);
        }
    }
    
    // 获取支配树的先序遍历（改为基于 CFG 的 RPO 遍历）
    private List<BasicBlock> getPreorder(Function func) {
        List<BasicBlock> result = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();
        BasicBlock entry = func.getBasicBlocks().getFirst();
        
        // 使用 DFS 后序遍历，然后逆序得到 RPO
        Deque<BasicBlock> postorder = new ArrayDeque<>();
        dfsPostorder(entry, visited, postorder);
        
        // 逆序得到 RPO
        while (!postorder.isEmpty()) {
            result.add(postorder.pollLast());
        }
        
        // 确保所有基本块都被遍历（处理不可达块）
        for (BasicBlock bb : func.getBasicBlocks()) {
            if (!visited.contains(bb)) {
                result.add(bb);
            }
        }
        
        return result;
    }
    
    private void dfsPostorder(BasicBlock bb, Set<BasicBlock> visited, Deque<BasicBlock> postorder) {
        if (visited.contains(bb)) return;
        visited.add(bb);
        
        for (BasicBlock succ : bb.getSuccessors()) {
            dfsPostorder(succ, visited, postorder);
        }
        
        postorder.addLast(bb);
    }
    
    private void processBlock(BasicBlock bb) {
        // 使用副本遍历，因为可能会删除指令
        List<Instruction> instList = new ArrayList<>(bb.getInstructions());
        
        for (Instruction inst : instList) {
            if (toRemove.contains(inst)) continue;
            
            // Process Store instruction - invalidate memory table
            if (inst instanceof StoreInst) {
                StoreInst store = (StoreInst) inst;
                Value ptr = store.getOperand(1);
                String memKey = getMemoryKey(ptr);
                
                // 只清除被修改的内存位置，而不是整个表
                if (memKey != null) {
                    memoryTable.remove(memKey);
                } else {
                    // 未知地址的 Store，保守地清除所有非全局变量
                    memoryTable.entrySet().removeIf(e -> !e.getKey().startsWith("GLOBAL:"));
                }
                continue;
            }
            
            // Process Call instruction - may modify memory
            if (inst instanceof CallInst) {
                CallInst call = (CallInst) inst;
                String funcName = call.getFunction().getName();
                // printf 和 getint 不会修改用户的全局变量
                if (!funcName.equals("@printf") && !funcName.equals("@getint")) {
                    // 其他函数可能修改全局变量，保守地清除
                    memoryTable.clear();
                }
                continue;
            }
            
            // DISABLED: Load消除 - 需要更复杂的数据流分析来处理循环
            // 当前简单实现无法正确处理循环中的 Load
            /*
            if (inst instanceof LoadInst) {
                LoadInst load = (LoadInst) inst;
                Value ptr = load.getOperand(0);
                String memKey = getMemoryKey(ptr);
                
                if (memKey != null) {
                    LoadInst prevLoad = memoryTable.get(memKey);
                    if (prevLoad != null && !toRemove.contains(prevLoad)) {
                        inst.replaceAllUsesWith(prevLoad);
                        toRemove.add(inst);
                        continue;
                    } else {
                        memoryTable.put(memKey, load);
                    }
                }
            }
            */
            
            // 只对可以进行值编号的指令操作
            if (canBeNumbered(inst)) {
                String key = computeKey(inst);
                if (key != null) {
                    Value existing = valueNumberTable.get(key);
                    if (existing != null && !toRemove.contains(existing)) {
                        // 找到了相同的表达式，替换
                        inst.replaceAllUsesWith(existing);
                        toRemove.add(inst);
                    } else {
                        // 新表达式，加入表
                        valueNumberTable.put(key, inst);
                    }
                }
            }
        }
    }
    
    // 判断指令是否可以进行值编号
    private boolean canBeNumbered(Instruction inst) {
        // 只对有使用者的纯计算指令进行编号
        if (inst.getUsers().isEmpty()) return false;
        
        // 二元运算指令
        if (inst instanceof BinaryInst) return true;
        
        // GEP 指令（数组索引）- 暂时禁用，可能导致跨作用域替换问题
        // if (inst instanceof GepInst) return true;
        
        // 可以扩展到其他纯计算指令...
        
        return false;
    }
    
    // 计算指令的唯一 key
    private String computeKey(Instruction inst) {
        if (inst instanceof BinaryInst) {
            return computeBinaryKey((BinaryInst) inst);
        }
        if (inst instanceof GepInst) {
            return computeGepKey((GepInst) inst);
        }
        return null;
    }
    
    // 计算二元运算的 key
    private String computeBinaryKey(BinaryInst inst) {
        BinaryInst.Operator op = inst.getOperator();
        Value lhs = inst.getOperand(0);
        Value rhs = inst.getOperand(1);
        
        // 获取操作数的规范名称
        String lhsName = getValueName(lhs);
        String rhsName = getValueName(rhs);
        
        // 对于可交换运算（加法、乘法），规范化操作数顺序
        if (isCommutative(op)) {
            if (lhsName.compareTo(rhsName) > 0) {
                String tmp = lhsName;
                lhsName = rhsName;
                rhsName = tmp;
            }
        }
        
        return "BINARY:" + op.name() + ":" + lhsName + ":" + rhsName;
    }
    
    
    // 获取内存位置的 key（用于 Load/Store 分析）
    private String getMemoryKey(Value ptr) {
        // 对于 GEP 指令，计算其 key
        if (ptr instanceof GepInst) {
            return computeGepKey((GepInst) ptr);
        }
        // 对于全局变量
        if (ptr instanceof GlobalVariable) {
            return "GLOBAL:" + ptr.getName();
        }
        // 对于 Alloca 指令（局部变量）
        if (ptr instanceof Instruction && 
            ((Instruction) ptr).getParent() != null) {
            return "ALLOCA:" + ptr.getName();
        }
        // 对于参数
        if (ptr instanceof Argument) {
            return "ARG:" + ptr.getName();
        }
        return null;
    }
    // 计算 GEP 的 key
    private String computeGepKey(GepInst inst) {
        StringBuilder sb = new StringBuilder("GEP:");
        for (int i = 0; i < inst.getNumOperands(); i++) {
            sb.append(getValueName(inst.getOperand(i))).append(":");
        }
        return sb.toString();
    }
    
    // 获取 Value 的规范名称
    private String getValueName(Value v) {
        // 对于常量，使用常量值
        if (v instanceof ConstInt) {
            return "CONST:" + ((ConstInt) v).getVal();
        }
        // 对于全局变量，使用名称
        if (v instanceof GlobalVariable) {
            return "GLOBAL:" + v.getName();
        }
        // 对于参数，使用名称
        if (v instanceof Argument) {
            return "ARG:" + v.getName();
        }
        // 对于指令，检查是否已经被值编号
        // 使用 Value 的唯一标识（对象的 hashCode 或名称）
        if (v instanceof Instruction) {
            // 如果这个指令已经被映射到一个更早的值，使用那个值的名称
            // 这里简单起见直接使用名称
            return "INST:" + v.getName();
        }
        // 默认使用名称
        return "VAL:" + v.getName();
    }
    
    // 判断运算是否可交换
    private boolean isCommutative(BinaryInst.Operator op) {
        return op == BinaryInst.Operator.ADD || op == BinaryInst.Operator.MUL;
    }
}
