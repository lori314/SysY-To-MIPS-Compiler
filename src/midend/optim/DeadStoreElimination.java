package midend.optim;

import midend.ir.values.BasicBlock;
import midend.ir.values.Function;
import midend.ir.values.Module;
import midend.ir.values.User;
import midend.ir.values.Value;
import midend.ir.values.instructions.*;

import java.util.*;

/**
 * Dead Store Elimination (DSE)
 * 
 * This pass removes stores to local allocas that are never read.
 * It is especially effective after constant propagation has eliminated
 * loads but left the corresponding stores in place.
 * 
 * Algorithm:
 * 1. For each function, find all alloca instructions
 * 2. For each alloca, check if it has any load users (directly or through GEP)
 * 3. If an alloca has no loads, remove all stores to it and the alloca itself
 */
public class DeadStoreElimination {

    public void run(Module module) {
        for (Function func : module.getFunctions()) {
            if (!func.getBasicBlocks().isEmpty()) {
                eliminateDeadStores(func);
            }
        }
    }

    private void eliminateDeadStores(Function func) {
        // Find all allocas and classify them
        Map<AllocaInst, Boolean> allocaHasLoad = new HashMap<>();
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof AllocaInst) {
                    allocaHasLoad.put((AllocaInst) inst, false);
                }
            }
        }
        
        // Check each alloca for load uses
        for (AllocaInst alloca : allocaHasLoad.keySet()) {
            if (hasLoadUse(alloca)) {
                allocaHasLoad.put(alloca, true);
            }
        }
        
        // Collect dead stores and allocas
        List<Instruction> toRemove = new ArrayList<>();
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof StoreInst) {
                    StoreInst store = (StoreInst) inst;
                    Value ptr = store.getPointer();
                    AllocaInst alloca = getAllocaBase(ptr);
                    
                    // Only remove stores to allocas that have no loads
                    // AND are not array/aggregate types (to be safe)
                    if (alloca != null && !allocaHasLoad.get(alloca)) {
                        // Check that the alloca is a simple scalar type
                        // to avoid removing stores to arrays/structs
                        if (isSimpleScalarAlloca(alloca)) {
                            toRemove.add(store);
                        }
                    }
                }
            }
        }
        
        // Also collect the allocas that have no loads
        for (Map.Entry<AllocaInst, Boolean> entry : allocaHasLoad.entrySet()) {
            if (!entry.getValue() && isSimpleScalarAlloca(entry.getKey())) {
                // Check if all uses are stores (to be removed) or GEPs with no load uses
                AllocaInst alloca = entry.getKey();
                boolean canRemove = true;
                
                for (User user : alloca.getUsers()) {
                    if (user instanceof StoreInst) {
                        // Will be removed
                    } else if (user instanceof GepInst) {
                        // GEP is OK if it has no load uses (already checked)
                    } else {
                        // Other use - can't remove
                        canRemove = false;
                        break;
                    }
                }
                
                if (canRemove) {
                    toRemove.add(alloca);
                }
            }
        }
        
        // Remove dead instructions
        for (Instruction inst : toRemove) {
            inst.remove();
        }
    }
    
    /**
     * Check if an alloca (or its derived pointers) has any load uses
     */
    private boolean hasLoadUse(AllocaInst alloca) {
        Set<Value> visited = new HashSet<>();
        Queue<Value> worklist = new LinkedList<>();
        worklist.add(alloca);
        
        while (!worklist.isEmpty()) {
            Value ptr = worklist.poll();
            if (visited.contains(ptr)) continue;
            visited.add(ptr);
            
            for (User user : ptr.getUsers()) {
                if (user instanceof LoadInst) {
                    return true;
                }
                if (user instanceof GepInst) {
                    worklist.add((GepInst) user);
                }
                if (user instanceof CallInst) {
                    // Pointer passed to function - conservatively assume it might be read
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Get the base alloca for a pointer (following GEP chain)
     */
    private AllocaInst getAllocaBase(Value ptr) {
        if (ptr instanceof AllocaInst) {
            return (AllocaInst) ptr;
        }
        if (ptr instanceof GepInst) {
            GepInst gep = (GepInst) ptr;
            return getAllocaBase(gep.getOperand(0));
        }
        return null;
    }
    
    /**
     * Check if an alloca is a simple scalar type (not array)
     */
    private boolean isSimpleScalarAlloca(AllocaInst alloca) {
        String typeStr = alloca.getAllocatedType().toString();
        // Simple check: not an array type
        return !typeStr.startsWith("[");
    }
}
