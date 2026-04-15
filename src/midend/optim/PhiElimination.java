package midend.optim;

import midend.ir.values.Module;
import midend.ir.values.Function;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;
import midend.ir.values.instructions.*;

import java.util.*;

/**
 * Phi Elimination Pass
 * 
 * 将 SSA 形式的 Phi 指令转换为显式的 store/load 指令。
 * 
 * 思路：对于 %x = phi i32 [%a, %bb1], [%b, %bb2]
 * 1. 创建一个 alloca 在入口块
 * 2. 在 bb1 的 terminator 之前: store %a, alloca
 * 3. 在 bb2 的 terminator 之前: store %b, alloca
 * 4. 在 phi 位置: %x = load alloca
 * 5. 删除 phi
 */
public class PhiElimination {
    
    public void run(Module module) {
        for (Function func : module.getFunctions()) {
            if (!func.getBasicBlocks().isEmpty()) {
                eliminatePhis(func);
            }
        }
    }
    
    private void eliminatePhis(Function func) {
        // 收集所有 Phi 指令
        List<PhiInst> phis = new ArrayList<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof PhiInst) {
                    phis.add((PhiInst) inst);
                }
            }
        }
        
        if (phis.isEmpty()) return;
        
        BasicBlock entryBlock = func.getBasicBlocks().getFirst();
        
        // 收集所有需要创建的 alloca 和对应的 phi
        List<AllocaInst> allocas = new ArrayList<>();
        Map<PhiInst, AllocaInst> phiToAlloca = new HashMap<>();
        
        // 第一遍：创建所有 alloca（在入口块）
        // 先记录入口块当前指令数
        int entryInstCount = entryBlock.getInstructions().size();
        
        for (PhiInst phi : phis) {
            // 创建 alloca，会自动添加到 entryBlock 末尾
            AllocaInst alloca = new AllocaInst(phi.getType(), entryBlock);
            allocas.add(alloca);
            phiToAlloca.put(phi, alloca);
        }
        
        // 将新创建的 alloca 移动到入口块头部
        LinkedList<Instruction> entryInsts = entryBlock.getInstructions();
        List<Instruction> newAllocas = new ArrayList<>();
        
        // 从末尾取出刚添加的 alloca
        for (int i = 0; i < allocas.size(); i++) {
            newAllocas.add(0, entryInsts.removeLast());
        }
        
        // 添加到头部
        for (Instruction alloca : newAllocas) {
            entryInsts.addFirst(alloca);
        }
        
        // 第二遍：为每个 phi 插入 store 和 load
        for (PhiInst phi : phis) {
            AllocaInst alloca = phiToAlloca.get(phi);
            eliminateSinglePhi(phi, alloca);
        }
    }
    
    private void eliminateSinglePhi(PhiInst phi, AllocaInst alloca) {
        BasicBlock phiBlock = phi.getParent();
        
        // 1. 在每个前驱块的 terminator 之前插入 store
        int numIncoming = phi.getNumIncoming();
        
        for (int i = 0; i < numIncoming; i++) {
            Value incomingVal = phi.getIncomingValue(i);
            BasicBlock predBlock = phi.getIncomingBlock(i);
            
            LinkedList<Instruction> predInsts = predBlock.getInstructions();
            if (predInsts.isEmpty()) continue;
            
            Instruction terminator = predInsts.getLast();
            
            // 创建 store (会自动添加到 predBlock 末尾)
            StoreInst store = new StoreInst(incomingVal, alloca, predBlock);
            
            // 移动 store 到 terminator 之前
            predInsts.removeLast(); // 移除刚添加的 store
            predInsts.removeLast(); // 移除 terminator
            predInsts.addLast(store);
            predInsts.addLast(terminator);
        }
        
        // 2. 在 phi 所在位置创建 load
        LoadInst load = new LoadInst(alloca, phiBlock);
        
        // 移动 load 到 phi 位置之后
        LinkedList<Instruction> phiBlockInsts = phiBlock.getInstructions();
        phiBlockInsts.removeLast(); // 移除刚添加的 load
        
        // 找到 phi 的索引
        int phiIndex = 0;
        for (Instruction inst : phiBlockInsts) {
            if (inst == phi) break;
            phiIndex++;
        }
        
        // 在 phi 后面插入 load
        phiBlockInsts.add(phiIndex + 1, load);
        
        // 3. 替换 phi 的使用
        phi.replaceAllUsesWith(load);
        
        // 4. 移除 phi
        phi.remove();
    }
}
