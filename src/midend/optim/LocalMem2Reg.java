package midend.optim;

import midend.ir.values.*;
import midend.ir.values.constants.ConstInt;
import midend.ir.values.instructions.*;

import java.util.*;

/**
 * LocalMem2Reg - 局部内存到寄存器优化
 * 
 * 只在单个基本块内消除 alloca -> store -> load 模式，不需要 Phi 节点。
 * 这是一个更保守但更安全的优化。
 * 
 * 优化模式:
 * 1. store X, ptr; ...; load ptr  -> 用 X 替代 load 的结果
 * 2. 追踪最近的 store 值
 */
public class LocalMem2Reg {
    
    public void run(midend.ir.values.Module module) {
        for (Function func : module.getFunctions()) {
            if (func.isBuiltin() || func.getBasicBlocks().isEmpty()) continue;
            
            // 识别可以提升的 alloca
            Set<AllocaInst> promotable = findPromotableAllocas(func);
            if (promotable.isEmpty()) continue;
            
            // 对每个基本块做局部优化
            for (BasicBlock bb : func.getBasicBlocks()) {
                optimizeBlock(bb, promotable);
            }
        }
    }
    
    /**
     * 找出可以提升的 alloca 指令
     * 条件：只被 load/store 使用，类型是 i32
     */
    private Set<AllocaInst> findPromotableAllocas(Function func) {
        Set<AllocaInst> result = new HashSet<>();
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst) {
                    AllocaInst alloca = (AllocaInst) inst;
                    if (isPromotable(alloca)) {
                        result.add(alloca);
                    }
                }
            }
        }
        
        return result;
    }
    
    private boolean isPromotable(AllocaInst alloca) {
        // 只支持 i32 类型
        if (!alloca.getAllocatedType().isInt()) return false;
        
        // 检查所有使用者
        for (User user : alloca.getUsers()) {
            if (user instanceof LoadInst) {
                // load 指令：alloca 必须是指针操作数
                if (((LoadInst) user).getOperand(0) != alloca) return false;
            } else if (user instanceof StoreInst) {
                // store 指令：alloca 必须是目标（第二个操作数）
                StoreInst store = (StoreInst) user;
                if (store.getOperand(1) != alloca) return false;
                // 不能把 alloca 存储到自己
                if (store.getOperand(0) == alloca) return false;
            } else {
                // 其他使用（如 GEP、call 参数等）不可提升
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 在单个基本块内进行 store-load 转发
     */
    private void optimizeBlock(BasicBlock bb, Set<AllocaInst> promotable) {
        // 追踪每个 alloca 的最新值
        Map<AllocaInst, Value> lastValue = new HashMap<>();
        
        // 要删除的指令
        List<Instruction> toRemove = new ArrayList<>();
        
        // 遍历指令
        List<Instruction> instructions = new ArrayList<>(bb.getInstructions());
        
        for (Instruction inst : instructions) {
            if (inst instanceof StoreInst) {
                StoreInst store = (StoreInst) inst;
                Value ptr = store.getOperand(1);
                Value val = store.getOperand(0);
                
                if (ptr instanceof AllocaInst && promotable.contains(ptr)) {
                    // 记录这个 alloca 的最新值
                    lastValue.put((AllocaInst) ptr, val);
                    // 注意：我们不在这里删除 store，因为跨块可能需要它
                    // 但如果后续有 load 可以转发，就记下来
                }
            } else if (inst instanceof LoadInst) {
                LoadInst load = (LoadInst) inst;
                Value ptr = load.getOperand(0);
                
                if (ptr instanceof AllocaInst && promotable.contains(ptr)) {
                    AllocaInst alloca = (AllocaInst) ptr;
                    Value cached = lastValue.get(alloca);
                    
                    if (cached != null) {
                        // 可以用 cached 值替代 load 结果！
                        load.replaceAllUsesWith(cached);
                        toRemove.add(load);
                    }
                    // 无论如何，load 之后我们仍保留该值（因为 load 本身就读取了最新值）
                    // 但如果 load 被删了，不更新；如果没删，其他 load 可以用这个 load 的结果
                    if (cached == null) {
                        // 没有已知值，但之后的 load 可以用这个 load 的结果
                        lastValue.put(alloca, load);
                    }
                }
            } else if (inst instanceof CallInst) {
                // 函数调用可能修改全局状态，清除所有追踪
                // 但对于局部 alloca，其地址没有逃逸，仍然是安全的
                // 这里保守起见，保留所有值（因为 alloca 是局部的）
            }
        }
        
        // 删除冗余的 load
        for (Instruction inst : toRemove) {
            inst.remove();
        }
    }
}
