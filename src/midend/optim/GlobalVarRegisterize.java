package midend.optim;

import midend.ir.types.PointerType;
import midend.ir.values.BasicBlock;
import midend.ir.values.Function;
import midend.ir.values.GlobalVariable;
import midend.ir.values.Value;
import midend.ir.values.instructions.*;

import java.util.*;

/**
 * Global Variable Registerization (Scalar Promotion)
 * 
 * For frequently accessed global variables in loops:
 * 1. Load the global variable into a local variable at the loop preheader
 * 2. Use the local variable inside the loop
 * 3. Store back to the global variable after the loop exits
 * 
 * This eliminates redundant global memory access inside hot loops.
 */
public class GlobalVarRegisterize {

    public void run(midend.ir.values.Module module) {
        for (Function func : module.getFunctions()) {
            if (!func.getBasicBlocks().isEmpty()) {
                registerizeGlobals(func);
            }
        }
    }

    private void registerizeGlobals(Function func) {
        // Find all global variable accesses
        Map<GlobalVariable, List<LoadInst>> globalLoads = new HashMap<>();
        Map<GlobalVariable, List<StoreInst>> globalStores = new HashMap<>();
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof LoadInst) {
                    LoadInst load = (LoadInst) inst;
                    Value ptr = load.getPointer();
                    if (ptr instanceof GlobalVariable && isScalarGlobal((GlobalVariable) ptr)) {
                        globalLoads.computeIfAbsent((GlobalVariable) ptr, k -> new ArrayList<>()).add(load);
                    }
                } else if (inst instanceof StoreInst) {
                    StoreInst store = (StoreInst) inst;
                    Value ptr = store.getPointer();
                    if (ptr instanceof GlobalVariable && isScalarGlobal((GlobalVariable) ptr)) {
                        globalStores.computeIfAbsent((GlobalVariable) ptr, k -> new ArrayList<>()).add(store);
                    }
                }
            }
        }
        
        // For each global variable with both loads and stores, try to registerize
        Set<GlobalVariable> candidates = new HashSet<>(globalLoads.keySet());
        candidates.retainAll(globalStores.keySet());
        
        if (candidates.isEmpty()) return;
        
        // Simple approach: Create alloca at function entry, load at start, store at end
        BasicBlock entryBlock = func.getBasicBlocks().getFirst();
        
        for (GlobalVariable gv : candidates) {
            List<LoadInst> loads = globalLoads.get(gv);
            List<StoreInst> stores = globalStores.get(gv);
            
            // Only optimize if there are enough accesses (likely in a loop)
            if (loads.size() + stores.size() < 3) continue;
            
            // Get the element type (global variable type is pointer)
            midend.ir.types.Type elemType = ((PointerType) gv.getType()).getPointeeType();
            
            // Create alloca at entry
            AllocaInst alloca = new AllocaInst(elemType, entryBlock);
            
            // Move alloca to the front of entry block
            LinkedList<Instruction> entryInsts = entryBlock.getInstructions();
            entryInsts.removeLast();
            entryInsts.addFirst(alloca);
            
            // Insert initial load from global to alloca at entry
            LoadInst initialLoad = new LoadInst(gv, entryBlock);
            entryInsts.removeLast();
            
            // Find first non-alloca instruction
            int insertPos = 0;
            for (Instruction inst : entryInsts) {
                if (!(inst instanceof AllocaInst)) break;
                insertPos++;
            }
            entryInsts.add(insertPos, initialLoad);
            
            // Store initial value to alloca
            StoreInst initialStore = new StoreInst(initialLoad, alloca, entryBlock);
            entryInsts.removeLast();
            entryInsts.add(insertPos + 1, initialStore);
            
            // Replace all loads from global with loads from alloca
            for (LoadInst load : loads) {
                // Create new load from alloca
                BasicBlock bb = load.getParent();
                LoadInst newLoad = new LoadInst(alloca, bb);
                
                // Move to correct position
                LinkedList<Instruction> insts = bb.getInstructions();
                insts.removeLast();
                
                int loadIdx = 0;
                for (Instruction inst : insts) {
                    if (inst == load) break;
                    loadIdx++;
                }
                insts.add(loadIdx, newLoad);
                
                // Replace uses
                load.replaceAllUsesWith(newLoad);
                load.remove();
            }
            
            // Replace all stores to global with stores to alloca
            for (StoreInst store : stores) {
                Value val = store.getValue();
                BasicBlock bb = store.getParent();
                
                StoreInst newStore = new StoreInst(val, alloca, bb);
                
                LinkedList<Instruction> insts = bb.getInstructions();
                insts.removeLast();
                
                int storeIdx = 0;
                for (Instruction inst : insts) {
                    if (inst == store) break;
                    storeIdx++;
                }
                insts.add(storeIdx, newStore);
                
                store.remove();
            }
            
            // Insert final store back to global before each return
            for (BasicBlock bb : func.getBasicBlocks()) {
                for (Instruction inst : new ArrayList<>(bb.getInstructions())) {
                    if (inst instanceof ReturnInst) {
                        // Load from alloca
                        LoadInst finalLoad = new LoadInst(alloca, bb);
                        LinkedList<Instruction> insts = bb.getInstructions();
                        insts.removeLast();
                        
                        int retIdx = 0;
                        for (Instruction i : insts) {
                            if (i == inst) break;
                            retIdx++;
                        }
                        insts.add(retIdx, finalLoad);
                        
                        // Store to global
                        StoreInst finalStore = new StoreInst(finalLoad, gv, bb);
                        insts.removeLast();
                        insts.add(retIdx + 1, finalStore);
                        
                        break;
                    }
                }
            }
        }
    }
    
    private boolean isScalarGlobal(GlobalVariable gv) {
        // Only promote scalar integers, not arrays
        midend.ir.types.Type elemType = ((PointerType) gv.getType()).getPointeeType();
        return elemType.isInt();
    }
}
