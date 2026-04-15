package midend.optim;

import midend.ir.values.BasicBlock;
import midend.ir.values.Function;
import midend.ir.values.Module;
import midend.ir.values.Value;
import midend.ir.values.instructions.Instruction;

import java.util.ArrayList;

public class InstructionSimplification {
    public void run(Module module) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Function func : module.getFunctions()) {
                for (BasicBlock bb : func.getBasicBlocks()) {
                    ArrayList<Instruction> instructions = new ArrayList<>(bb.getInstructions());
                    for (Instruction inst : instructions) {
                        // Skip if already removed (though we iterate a copy, the inst object is same)
                        // But inst.getParent() will be null if removed.
                        if (inst.getParent() == null) continue;

                        Value simplified = InstructionSimplify.simplify(inst);
                        if (simplified != inst) {
                            inst.replaceAllUsesWith(simplified);
                            inst.remove(); 
                            changed = true;
                        }
                    }
                }
            }
        }
    }
}
