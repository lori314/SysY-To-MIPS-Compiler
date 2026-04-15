package midend.optim;

import midend.ir.values.BasicBlock;
import midend.ir.values.Function;
import midend.ir.values.Module;
import midend.ir.values.Value;
import midend.ir.values.instructions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class DeadCodeElimination {
    private final Set<Instruction> usefulInstructions = new HashSet<>();

    public void run(Module module) {
        for (Function func : module.getFunctions()) {
            if (!func.getBasicBlocks().isEmpty()) {
                eliminateDeadCode(func);
            }
        }
    }

    private void eliminateDeadCode(Function func) {
        usefulInstructions.clear();
        
        // 1. Mark useful instructions
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (isCritical(inst)) {
                    markUseful(inst);
                }
            }
        }

        // 2. Sweep useless instructions
        for (BasicBlock bb : func.getBasicBlocks()) {
            ArrayList<Instruction> toRemove = new ArrayList<>();
            for (Instruction inst : bb.getInstructions()) {
                if (!usefulInstructions.contains(inst)) {
                    toRemove.add(inst);
                }
            }
            
            for (Instruction inst : toRemove) {
                // Replace uses with 0/undef before removing to avoid dangling pointers?
                // Actually Instruction.remove() handles operand use removal.
                // But if other instructions use this dead instruction, they must be dead too (otherwise this would be in closure).
                // So it is safe to remove.
                inst.remove();
            }
        }
    }

    private boolean isCritical(Instruction inst) {
        // Terminator (Br, Ret)
        if (inst.isTerminator()) return true;
        // Memory Write (Store)
        if (inst instanceof StoreInst) return true;
        // Function Call (Call) - Conservative assumption
        if (inst instanceof CallInst) return true;
        
        return false;
    }

    private void markUseful(Instruction inst) {
        if (usefulInstructions.contains(inst)) return;
        usefulInstructions.add(inst);
        
        for (int i = 0; i < inst.getNumOperands(); i++) {
            Value op = inst.getOperand(i);
            if (op instanceof Instruction) {
                markUseful((Instruction) op);
            }
        }
    }
}
