package midend.optim;

import midend.ir.values.*;
import midend.ir.values.instructions.*;

import java.util.*;

/**
 * GCM (Global Code Motion) - 全局代码移动优化
 * 
 * 简化实现：只做循环不变式外提 (LICM - Loop Invariant Code Motion)
 * 将循环内不依赖循环变量的指令移动到循环前
 */
public class GCM {
    
    private DomAnalysis domAnalysis;
    
    // 循环头 -> 循环前驱块（用于插入不变式）
    private final Map<BasicBlock, BasicBlock> loopPreheader = new HashMap<>();
    // 循环头 -> 循环体块集合
    private final Map<BasicBlock, Set<BasicBlock>> loopBodies = new HashMap<>();
    
    public void run(midend.ir.values.Module module) {
        // 确保 CFG 已经建立
        CFGBuilder.run(module);
        
        domAnalysis = new DomAnalysis();
        domAnalysis.run(module);
        
        for (Function func : module.getFunctions()) {
            if (func.isBuiltin() || func.getBasicBlocks().isEmpty()) continue;
            runOnFunction(func);
        }
    }
    
    private void runOnFunction(Function func) {
        loopPreheader.clear();
        loopBodies.clear();
        
        // 识别自然循环
        identifyLoops(func);
        
        // 对每个循环执行不变式外提
        for (BasicBlock header : loopBodies.keySet()) {
            hoistLoopInvariants(header);
        }
    }
    
    // 识别自然循环
    private void identifyLoops(Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (BasicBlock succ : bb.getSuccessors()) {
                // 后向边：succ 支配 bb
                if (domAnalysis.dominates(succ, bb)) {
                    // succ 是循环头，bb 是回边源
                    Set<BasicBlock> body = computeLoopBody(succ, bb);
                    loopBodies.put(succ, body);
                    
                    // 找到循环的前驱块（用于插入）
                    for (BasicBlock pred : succ.getPredecessors()) {
                        if (!body.contains(pred)) {
                            loopPreheader.put(succ, pred);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    // 计算循环体
    private Set<BasicBlock> computeLoopBody(BasicBlock header, BasicBlock back) {
        Set<BasicBlock> body = new HashSet<>();
        body.add(header);
        
        if (header == back) return body;
        
        Deque<BasicBlock> worklist = new ArrayDeque<>();
        body.add(back);
        worklist.add(back);
        
        while (!worklist.isEmpty()) {
            BasicBlock curr = worklist.poll();
            for (BasicBlock pred : curr.getPredecessors()) {
                if (!body.contains(pred)) {
                    body.add(pred);
                    worklist.add(pred);
                }
            }
        }
        
        return body;
    }
    
    // 循环内被修改的内存位置
    private Set<Value> loopModifiedMemory = new HashSet<>();
    
    // 对一个循环执行不变式外提
    private void hoistLoopInvariants(BasicBlock header) {
        Set<BasicBlock> body = loopBodies.get(header);
        BasicBlock preheader = loopPreheader.get(header);
        
        if (preheader == null || body == null) return;
        
        // 收集循环内定义的值
        Set<Value> loopDefs = new HashSet<>();
        loopModifiedMemory.clear();
        
        for (BasicBlock bb : body) {
            for (Instruction inst : bb.getInstructions()) {
                loopDefs.add(inst);
                
                // 收集循环内被修改的内存位置
                if (inst instanceof StoreInst) {
                    Value ptr = inst.getOperand(1);
                    loopModifiedMemory.add(ptr);
                }
            }
        }
        
        // 反复尝试外提，直到没有变化
        boolean changed = true;
        int maxIter = 100; // 防止无限循环
        while (changed && maxIter-- > 0) {
            changed = false;
            
            for (BasicBlock bb : new ArrayList<>(body)) {
                List<Instruction> toHoist = new ArrayList<>();
                
                for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                    if (canBeHoisted(inst, loopDefs) && isLoopInvariant(inst, loopDefs)) {
                        toHoist.add(inst);
                    }
                }
                
                for (Instruction inst : toHoist) {
                    // 从原块移除
                    bb.removeInstruction(inst);
                    // 插入到 preheader
                    preheader.insertBeforeTerminator(inst);
                    // 从循环定义中移除
                    loopDefs.remove(inst);
                    changed = true;
                }
            }
        }
    }
    
    // 判断指令是否可以被外提
    private boolean canBeHoisted(Instruction inst, Set<Value> loopDefs) {
        if (inst.isTerminator()) return false;
        if (inst instanceof PhiInst) return false;
        if (inst instanceof StoreInst) return false;
        if (inst instanceof CallInst) return false;
        if (inst instanceof AllocaInst) return false;
        
        // Load 不外提 - 寄存器分配器无法正确处理跨循环使用
        // TODO: 需要更智能的活跃区间分析来处理循环
        if (inst instanceof LoadInst) {
            return false;
        }
        
        // GEP 也不外提 - 同样的寄存器分配问题
        if (inst instanceof GepInst) {
            return false;
        }
        
        return (inst instanceof BinaryInst) || 
               (inst instanceof IcmpInst) ||
               (inst instanceof ZextInst);
    }
    
    // 判断指令是否是循环不变式
    private boolean isLoopInvariant(Instruction inst, Set<Value> loopDefs) {
        for (int i = 0; i < inst.getNumOperands(); i++) {
            Value operand = inst.getOperand(i);
            // 如果操作数在循环内定义，则不是不变式
            if (loopDefs.contains(operand)) {
                return false;
            }
        }
        return true;
    }
}
