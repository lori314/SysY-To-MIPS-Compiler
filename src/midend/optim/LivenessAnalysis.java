package midend.optim;

import midend.ir.values.BasicBlock;
import midend.ir.values.Function;
import midend.ir.values.Value;
import midend.ir.values.constants.ConstInt;
import midend.ir.values.instructions.*;

import java.util.*;

/**
 * 活跃性分析 (Liveness Analysis)
 * 用于计算每个变量的活跃区间 (Live Interval)
 */
public class LivenessAnalysis {
    
    // 每个基本块的 LiveIn 和 LiveOut 集合
    private final Map<BasicBlock, Set<Value>> liveIn = new HashMap<>();
    private final Map<BasicBlock, Set<Value>> liveOut = new HashMap<>();
    
    // 每条指令的行号 (用于计算活跃区间)
    private final Map<Instruction, Integer> instrIndex = new HashMap<>();
    
    // 每个变量的活跃区间 [start, end]
    private final Map<Value, LiveInterval> liveIntervals = new HashMap<>();
    
    // 反向后序遍历顺序
    private List<BasicBlock> reversePostOrder;
    
    public void analyze(Function func) {
        if (func.getBasicBlocks().isEmpty()) return;
        
        // 1. 计算基本块的顺序
        computeBlockOrder(func);
        
        // 2. 给每条指令编号
        numberInstructions(func);
        
        // 3. 计算每个基本块的 USE 和 DEF 集合
        Map<BasicBlock, Set<Value>> blockUse = new HashMap<>();
        Map<BasicBlock, Set<Value>> blockDef = new HashMap<>();
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            Set<Value> use = new LinkedHashSet<>();
            Set<Value> def = new LinkedHashSet<>();
            
            for (Instruction inst : bb.getInstructions()) {
                // 先处理 use (在 def 之前使用的变量)
                for (Value op : getUses(inst)) {
                    if (!def.contains(op) && isVariable(op)) {
                        use.add(op);
                    }
                }
                // 再处理 def
                if (hasResult(inst)) {
                    def.add(inst);
                }
            }
            
            blockUse.put(bb, use);
            blockDef.put(bb, def);
            liveIn.put(bb, new LinkedHashSet<>());
            liveOut.put(bb, new LinkedHashSet<>());
        }
        
        // 4. 迭代计算 LiveIn 和 LiveOut (使用反向数据流分析)
        boolean changed = true;
        while (changed) {
            changed = false;
            
            // 反向遍历
            for (int i = reversePostOrder.size() - 1; i >= 0; i--) {
                BasicBlock bb = reversePostOrder.get(i);
                
                // LiveOut[B] = ∪ LiveIn[S], for all successors S of B
                Set<Value> newLiveOut = new LinkedHashSet<>();
                for (BasicBlock succ : bb.getSuccessors()) {
                    newLiveOut.addAll(liveIn.get(succ));
                }
                
                // LiveIn[B] = Use[B] ∪ (LiveOut[B] - Def[B])
                Set<Value> newLiveIn = new LinkedHashSet<>(blockUse.get(bb));
                Set<Value> liveOutMinusDef = new LinkedHashSet<>(newLiveOut);
                liveOutMinusDef.removeAll(blockDef.get(bb));
                newLiveIn.addAll(liveOutMinusDef);
                
                if (!newLiveIn.equals(liveIn.get(bb)) || !newLiveOut.equals(liveOut.get(bb))) {
                    liveIn.put(bb, newLiveIn);
                    liveOut.put(bb, newLiveOut);
                    changed = true;
                }
            }
        }
        
        // 5. 计算每个变量的活跃区间
        computeLiveIntervals(func);
    }
    
    private void computeBlockOrder(Function func) {
        reversePostOrder = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();
        
        // DFS 后序遍历
        List<BasicBlock> postOrder = new ArrayList<>();
        dfsPostOrder(func.getBasicBlocks().getFirst(), visited, postOrder);
        
        // 反转得到反向后序
        for (int i = postOrder.size() - 1; i >= 0; i--) {
            reversePostOrder.add(postOrder.get(i));
        }
        
        // 添加未访问的块 (不可达块)
        for (BasicBlock bb : func.getBasicBlocks()) {
            if (!visited.contains(bb)) {
                reversePostOrder.add(bb);
            }
        }
    }
    
    private void dfsPostOrder(BasicBlock bb, Set<BasicBlock> visited, List<BasicBlock> postOrder) {
        if (visited.contains(bb)) return;
        visited.add(bb);
        
        for (BasicBlock succ : bb.getSuccessors()) {
            dfsPostOrder(succ, visited, postOrder);
        }
        
        postOrder.add(bb);
    }
    
    private void numberInstructions(Function func) {
        int index = 0;
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                instrIndex.put(inst, index);
                index += 2; // 留出间隙用于插入 spill 代码
            }
        }
    }
    
    private void computeLiveIntervals(Function func) {
        liveIntervals.clear();
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            List<Instruction> insts = new ArrayList<>(bb.getInstructions());
            if (insts.isEmpty()) continue;
            
            int blockStart = instrIndex.get(insts.getFirst());
            int blockEnd = instrIndex.get(insts.getLast());
            
            // 对于 LiveOut 中的变量，它们在整个块中都是活跃的
            for (Value v : liveOut.get(bb)) {
                extendInterval(v, blockStart, blockEnd + 1);
            }
            
            // 反向遍历指令
            Set<Value> live = new LinkedHashSet<>(liveOut.get(bb));
            
            for (int i = insts.size() - 1; i >= 0; i--) {
                Instruction inst = insts.get(i);
                int instIdx = instrIndex.get(inst);
                
                // 定义点：从 live 中移除，设置区间起点
                if (hasResult(inst)) {
                    live.remove(inst);
                    // 区间至少包含定义点
                    if (!liveIntervals.containsKey(inst)) {
                        liveIntervals.put(inst, new LiveInterval(instIdx, instIdx + 1));
                    } else {
                        liveIntervals.get(inst).start = instIdx;
                    }
                }
                
                // 使用点：添加到 live，扩展区间
                for (Value op : getUses(inst)) {
                    if (isVariable(op)) {
                        live.add(op);
                        extendInterval(op, blockStart, instIdx + 1);
                    }
                }
            }
        }
        
        // 处理函数参数
        for (Value arg : func.getArguments()) {
            if (!liveIntervals.containsKey(arg)) {
                liveIntervals.put(arg, new LiveInterval(0, 1));
            } else {
                liveIntervals.get(arg).start = 0;
            }
        }
    }
    
    private void extendInterval(Value v, int start, int end) {
        if (liveIntervals.containsKey(v)) {
            LiveInterval interval = liveIntervals.get(v);
            interval.start = Math.min(interval.start, start);
            interval.end = Math.max(interval.end, end);
        } else {
            liveIntervals.put(v, new LiveInterval(start, end));
        }
    }
    
    private List<Value> getUses(Instruction inst) {
        List<Value> uses = new ArrayList<>();
        for (int i = 0; i < inst.getNumOperands(); i++) {
            Value op = inst.getOperand(i);
            if (isVariable(op)) {
                uses.add(op);
            }
        }
        return uses;
    }
    
    private boolean isVariable(Value v) {
        if (v instanceof ConstInt) return false;
        if (v instanceof BasicBlock) return false;
        if (v instanceof Function) return false;
        if (v instanceof midend.ir.values.GlobalVariable) return false;
        return v instanceof Instruction || v instanceof midend.ir.values.Argument;
    }
    
    private boolean hasResult(Instruction inst) {
        // 没有结果的指令
        if (inst instanceof StoreInst) return false;
        if (inst instanceof BranchInst) return false;
        if (inst instanceof ReturnInst) return false;
        // Call 指令如果返回 void 则没有结果
        if (inst instanceof CallInst) {
            return !inst.getType().isVoid();
        }
        return true;
    }
    
    public Map<Value, LiveInterval> getLiveIntervals() {
        return liveIntervals;
    }
    
    public Map<Instruction, Integer> getInstrIndex() {
        return instrIndex;
    }
    
    /**
     * 活跃区间
     */
    public static class LiveInterval implements Comparable<LiveInterval> {
        public int start;
        public int end;
        public Value value;
        
        public LiveInterval(int start, int end) {
            this.start = start;
            this.end = end;
        }
        
        public int length() {
            return end - start;
        }
        
        public boolean overlaps(LiveInterval other) {
            return this.start < other.end && other.start < this.end;
        }
        
        @Override
        public int compareTo(LiveInterval o) {
            return Integer.compare(this.start, o.start);
        }
        
        @Override
        public String toString() {
            return "[" + start + ", " + end + ")";
        }
    }
}
