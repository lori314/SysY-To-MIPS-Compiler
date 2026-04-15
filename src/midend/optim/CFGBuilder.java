package midend.optim;

import midend.ir.values.BasicBlock;
import midend.ir.values.Function;
import midend.ir.values.Module;
import midend.ir.values.instructions.BranchInst;
import midend.ir.values.instructions.Instruction;
import midend.ir.values.instructions.ReturnInst;

import java.util.ArrayList;

public class CFGBuilder {
    public static void run(Module module) {
        for (Function func : module.getFunctions()) {
            if (!func.getBasicBlocks().isEmpty()) {
                buildForFunction(func);
            }
        }
    }

    private static void buildForFunction(Function func) {
        // 1. 清除旧关系
        for (BasicBlock bb : func.getBasicBlocks()) {
            bb.cleanSuccessors();
        }

        // 2. 重新连接
        for (BasicBlock bb : func.getBasicBlocks()) {
            if (bb.getInstructions().isEmpty()) continue;
            
            Instruction terminator = bb.getInstructions().getLast();
            
            if (terminator instanceof BranchInst) {
                BranchInst br = (BranchInst) terminator;
                if (br.isConditional()) {
                    // br i1 %cond, label %true, label %false
                    // 操作数 1 是 trueBB, 操作数 2 是 falseBB (操作数0是cond)
                    BasicBlock trueBB = (BasicBlock) br.getOperand(1);
                    BasicBlock falseBB = (BasicBlock) br.getOperand(2);
                    
                    link(bb, trueBB);
                    link(bb, falseBB);
                } else {
                    // br label %dest
                    BasicBlock destBB = (BasicBlock) br.getOperand(0);
                    link(bb, destBB);
                }
            }
            // ReturnInst 没有后继
        }
    }

    private static void link(BasicBlock pred, BasicBlock succ) {
        if (!pred.getSuccessors().contains(succ)) {
            pred.getSuccessors().add(succ);
        }
        if (!succ.getPredecessors().contains(pred)) {
            succ.getPredecessors().add(pred);
        }
    }
}
