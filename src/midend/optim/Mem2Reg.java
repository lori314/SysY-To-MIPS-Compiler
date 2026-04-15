package midend.optim;

import midend.ir.types.IntType;
import midend.ir.values.*;
import midend.ir.values.instructions.*;
import midend.ir.values.Module;
import midend.ir.values.constants.ConstInt;

import java.util.*;

public class Mem2Reg {
    private DomAnalysis domAnalysis;
    
    public void run(Module module) {
        CFGBuilder.run(module);
        domAnalysis = new DomAnalysis();
        domAnalysis.run(module);
        
        for (Function func : module.getFunctions()) {
            if (!func.getBasicBlocks().isEmpty())
                promoteMemoryToRegister(func);
        }
    }

    private void promoteMemoryToRegister(Function func) {
        ArrayList<AllocaInst> promotableAllocas = new ArrayList<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst && isPromotable((AllocaInst) inst)) {
                    promotableAllocas.add((AllocaInst) inst);
                }
            }
        }
        
        if (promotableAllocas.isEmpty()) return;

        // 记录 Phi 指令对应的 Alloca，方便 Rename
        Map<PhiInst, AllocaInst> phiToAlloca = new HashMap<>();

        // 1. 插入 Phi
        for (AllocaInst alloca : promotableAllocas) {
            Set<BasicBlock> defBlocks = new HashSet<>();
            for (User user : alloca.getUsers()) {
                if (user instanceof StoreInst && ((StoreInst) user).getOperand(1) == alloca) {
                    defBlocks.add(((Instruction) user).getParent());
                }
            }

            Set<BasicBlock> phiBlocks = new HashSet<>();
            Queue<BasicBlock> workList = new LinkedList<>(defBlocks);
            
            while (!workList.isEmpty()) {
                BasicBlock X = workList.poll();
                for (BasicBlock Y : domAnalysis.getDomFrontier(X)) {
                    if (!phiBlocks.contains(Y)) {
                        // 在 Y 头部插入 Phi
                        PhiInst phi = new PhiInst(alloca.getAllocatedType(), Y);
                        // 移动 Phi 到指令列表最前
                        Y.getInstructions().removeLast(); // 刚构造时自动加到了尾部
                        Y.getInstructions().addFirst(phi); 
                        
                        phiToAlloca.put(phi, alloca);
                        phiBlocks.add(Y);
                        if (!defBlocks.contains(Y)) workList.add(Y);
                    }
                }
            }
        }

        // 2. 重命名
        Map<AllocaInst, Stack<Value>> valueStacks = new HashMap<>();
        for (AllocaInst a : promotableAllocas) {
            Stack<Value> s = new Stack<>();
            // 默认值 0 (undef)
            s.push(ConstInt.ZERO); 
            valueStacks.put(a, s);
        }

        // Build DomTree Children Map
        Map<BasicBlock, List<BasicBlock>> domTreeChildren = new HashMap<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            domTreeChildren.put(bb, new ArrayList<>());
        }
        for (BasicBlock bb : func.getBasicBlocks()) {
            BasicBlock idom = domAnalysis.getIDom(bb);
            if (idom != null && idom != bb) {
                domTreeChildren.get(idom).add(bb);
            }
        }

        rename(func.getBasicBlocks().getFirst(), valueStacks, phiToAlloca, new HashSet<>(), domTreeChildren);

        // 3. 清理垃圾
        for (AllocaInst a : promotableAllocas) a.remove();
    }

    private void rename(BasicBlock bb, Map<AllocaInst, Stack<Value>> valueStacks, 
                        Map<PhiInst, AllocaInst> phiToAlloca, Set<BasicBlock> visited,
                        Map<BasicBlock, List<BasicBlock>> domTreeChildren) {
        if (visited.contains(bb)) return;
        visited.add(bb);

        // 记录当前块压入栈的操作数数量，方便回溯时弹出
        Map<AllocaInst, Integer> pushCount = new HashMap<>();

        // 遍历指令
        // 注意：要在遍历过程中删除指令，使用迭代器或副本
        List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
        
        for (Instruction inst : instructions) {
            if (inst instanceof PhiInst) {
                // 定义：Phi 更新了栈顶
                AllocaInst alloca = phiToAlloca.get(inst);
                if (alloca != null) {
                    valueStacks.get(alloca).push(inst);
                    pushCount.merge(alloca, 1, Integer::sum);
                }
            } else if (inst instanceof LoadInst) {
                // 使用：Load 被替换为栈顶值
                Value ptr = inst.getOperand(0);
                if (ptr instanceof AllocaInst && valueStacks.containsKey(ptr)) {
                    Value currVal = valueStacks.get(ptr).peek();
                    inst.replaceAllUsesWith(currVal);
                    // 标记 Load 为死代码 (稍后移除)
                    inst.remove(); 
                }
            } else if (inst instanceof StoreInst) {
                // 定义：Store 更新了栈顶
                Value ptr = inst.getOperand(1);
                Value val = inst.getOperand(0);
                if (ptr instanceof AllocaInst && valueStacks.containsKey(ptr)) {
                    valueStacks.get(ptr).push(val);
                    pushCount.merge((AllocaInst) ptr, 1, Integer::sum);
                    // 标记 Store 为死代码
                    inst.remove();
                }
            }
        }

        // 填充后继块中 Phi 的参数
        for (BasicBlock succ : bb.getSuccessors()) {
            for (Instruction inst : succ.getInstructions()) {
                if (inst instanceof PhiInst) {
                    AllocaInst alloca = phiToAlloca.get(inst);
                    if (alloca != null) {
                        Stack<Value> stack = valueStacks.get(alloca);
                        if (!stack.isEmpty()) {
                            ((PhiInst) inst).addIncoming(stack.peek(), bb);
                        } else {
                            // 理论上不应发生，除非使用未初始化变量
                            ((PhiInst) inst).addIncoming(ConstInt.ZERO, bb);
                        }
                    }
                } else {
                    // Phi 节点都在块头，遇到非 Phi 可以停止
                    break;
                }
            }
        }

        // 递归支配树子节点
        for (BasicBlock child : domTreeChildren.get(bb)) {
            rename(child, valueStacks, phiToAlloca, visited, domTreeChildren);
        }

        // 回溯：恢复栈状态
        for (Map.Entry<AllocaInst, Integer> entry : pushCount.entrySet()) {
            Stack<Value> s = valueStacks.get(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                s.pop();
            }
        }
    }

    private boolean isPromotable(AllocaInst alloca) {
        // 只支持 Int 类型 (SysY没有float/double)
        if (!alloca.getAllocatedType().isInt()) return false;
        
        for (User user : alloca.getUsers()) {
            if (user instanceof LoadInst) {
                if (user.getOperand(0) != alloca) return false;
            } else if (user instanceof StoreInst) {
                if (user.getOperand(1) != alloca) return false; 
                if (user.getOperand(0) == alloca) return false;
            } else {
                return false;
            }
        }
        return true;
    }
}
