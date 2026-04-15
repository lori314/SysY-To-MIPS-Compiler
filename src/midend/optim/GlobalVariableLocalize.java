package midend.optim;

import midend.ir.types.PointerType;
import midend.ir.types.Type;
import midend.ir.values.Module;
import midend.ir.values.*;
import midend.ir.values.instructions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GlobalVariableLocalize {
    private final Map<GlobalVariable, Set<Function>> globalUsers = new HashMap<>();

    public void run(Module module) {
        analyzeGlobalUsage(module);
        localizeGlobals(module);
    }

    private void analyzeGlobalUsage(Module module) {
        globalUsers.clear();
        for (GlobalVariable gv : module.getGlobalVariables()) {
            Set<Function> users = new HashSet<>();
            for (User user : gv.getUsers()) {
                if (user instanceof Instruction) {
                    Function func = ((Instruction) user).getParent().getParent();
                    users.add(func);
                }
            }
            globalUsers.put(gv, users);
        }
    }

    private void localizeGlobals(Module module) {
        // Use iterator to allow removal
        ArrayList<GlobalVariable> globals = new ArrayList<>(module.getGlobalVariables());
        
        for (GlobalVariable gv : globals) {
            Set<Function> users = globalUsers.get(gv);
            if (users == null || users.isEmpty()) {
                // Unused global
                // module.getGlobalVariables().remove(gv); // Safe to remove? Yes.
                continue;
            }

            if (users.size() == 1) {
                Function func = users.iterator().next();
                
                // Only localize in main function to ensure safety regarding static variables / persistence.
                // Or if we can prove it's not live-in (written before read).
                // For now, restricting to main is safe.
                if (!func.getName().equals("@main")) {
                    continue;
                }

                if (hasCalls(func)) {
                    continue;
                }

                localize(gv, func);
                module.getGlobalVariables().remove(gv);
            }
        }
    }

    private boolean hasCalls(Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst) return true;
            }
        }
        return false;
    }

    private void localize(GlobalVariable gv, Function func) {
        BasicBlock entry = func.getBasicBlocks().get(0);
        Type elemType = ((PointerType) gv.getType()).getPointeeType();
        
        // Create Alloca
        AllocaInst alloca = new AllocaInst(elemType, entry);
        // Move to front
        entry.getInstructions().removeLast();
        entry.getInstructions().addFirst(alloca);
        
        // Initialize if needed
        // Global variables are zero-initialized or have constant init.
        // We need to store the init value at the beginning.
        if (gv.getInitializer() != null) {
            // Create Store
            StoreInst store = new StoreInst(gv.getInitializer(), alloca, entry);
            // Move to after alloca
            entry.getInstructions().removeLast();
            entry.getInstructions().add(1, store);
        }

        // Replace uses
        gv.replaceAllUsesWith(alloca);
    }
}
