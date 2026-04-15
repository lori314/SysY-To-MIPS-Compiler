package midend.optim;

import midend.ir.types.VoidType;
import midend.ir.values.*;
import midend.ir.values.Module;
import midend.ir.values.instructions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class InlineFunction {
    private final Set<Function> recursiveFuncs = new HashSet<>();
    private final FunctionClone cloner;
    private int inlineCounter = 0;
    
    public InlineFunction() {
        this.cloner = null; // Initialized per function
    }

    public void run(Module module) {
        boolean changed = true;
        while (changed) {
            changed = false;
            analyzeRecursion(module);
            
            for (Function caller : module.getFunctions()) {
                if (caller.isBuiltin()) continue;
                
                // Collect call sites first to avoid modification during iteration
                ArrayList<CallInst> callsToInline = new ArrayList<>();
                for (BasicBlock bb : caller.getBasicBlocks()) {
                    for (Instruction inst : bb.getInstructions()) {
                        if (inst instanceof CallInst) {
                            CallInst call = (CallInst) inst;
                            Function callee = call.getFunction();
                            if (shouldInline(callee)) {
                                callsToInline.add(call);
                            }
                        }
                    }
                }
                
                for (CallInst call : callsToInline) {
                    inlineCall(caller, call);
                    changed = true;
                }
            }
        }
    }

    private boolean shouldInline(Function func) {
        if (func.isBuiltin()) return false;
        if (func.getName().equals("@main")) return false;
        if (recursiveFuncs.contains(func)) return false;
        
        // Heuristic: Instruction count limit?
        // For now, inline all non-recursive functions (aggressive)
        // Or limit size.
        int instCount = 0;
        for (BasicBlock bb : func.getBasicBlocks()) {
            instCount += bb.getInstructions().size();
        }
        return instCount < 500; // Reasonable limit
    }

    private void analyzeRecursion(Module module) {
        recursiveFuncs.clear();
        // Simple DFS to detect cycles or self-recursion
        // For now, just check direct recursion and simple cycles?
        // Or just direct recursion for safety.
        for (Function f : module.getFunctions()) {
            if (calls(f, f, new HashSet<>())) {
                recursiveFuncs.add(f);
            }
        }
    }

    private boolean calls(Function current, Function target, Set<Function> visited) {
        if (visited.contains(current)) return false;
        visited.add(current);
        
        for (BasicBlock bb : current.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst) {
                    Function callee = ((CallInst) inst).getFunction();
                    if (callee == target) return true;
                    if (!callee.isBuiltin() && calls(callee, target, visited)) return true;
                }
            }
        }
        return false;
    }

    private void inlineCall(Function caller, CallInst call) {
        Function callee = call.getFunction();
        BasicBlock callBlock = call.getParent();
        
        // 1. Split the block at call site
        BasicBlock splitBlock = new BasicBlock(callBlock.getName() + "_split_" + inlineCounter, caller);
        
        // Move instructions after call to splitBlock
        ArrayList<Instruction> insts = new ArrayList<>(callBlock.getInstructions());
        int callIndex = insts.indexOf(call);
        
        // Move successors from callBlock to splitBlock
        splitBlock.getSuccessors().addAll(callBlock.getSuccessors());
        callBlock.getSuccessors().clear();
        // Update predecessors of successors
        for (BasicBlock succ : splitBlock.getSuccessors()) {
            succ.getPredecessors().remove(callBlock);
            succ.getPredecessors().add(splitBlock);
            // Update Phi nodes in successors
            for (Instruction i : succ.getInstructions()) {
                if (i instanceof PhiInst) {
                    // PhiInst not implemented fully yet, but if it exists:
                    // ((PhiInst) i).replaceIncomingBlock(callBlock, splitBlock);
                }
            }
        }

        for (int i = callIndex + 1; i < insts.size(); i++) {
            Instruction inst = insts.get(i);
            // Remove from old list directly (using iterator would be better but list is arraylist copy)
            callBlock.getInstructions().remove(inst);
            
            // Add to new block
            splitBlock.getInstructions().add(inst);
            inst.setParent(splitBlock);
        }

        // 2. Handle Return Value (Alloca)
        Value retAddr = null;
        if (!call.getType().isVoid()) {
            // Create alloca in entry block of caller
            BasicBlock entry = caller.getBasicBlocks().getFirst();
            AllocaInst alloca = new AllocaInst(call.getType(), entry);
            // Move to front
            entry.getInstructions().removeLast();
            entry.getInstructions().addFirst(alloca);
            retAddr = alloca;
        }

        // 3. Clone Callee
        FunctionClone cloner = new FunctionClone(caller);
        ArrayList<Value> args = new ArrayList<>();
        for (int i = 1; i < call.getNumOperands(); i++) {
            args.add(call.getOperand(i));
        }
        
        FunctionClone.CloneResult result = cloner.cloneForInlining(callee, args, splitBlock, inlineCounter++);
        
        // 4. Connect CallBlock -> Entry of Clone
        new BranchInst(result.entry, callBlock);
        
        // 5. Handle Returns in Clone
        for (ReturnInst ret : result.returns) {
            BasicBlock retBlock = ret.getParent();
            if (retAddr != null && ret.getNumOperands() > 0) {
                new StoreInst(ret.getOperand(0), retAddr, retBlock);
            }
            new BranchInst(splitBlock, retBlock);
            ret.remove();
        }
        
        // 6. Replace Call Uses
        if (retAddr != null) {
            // Load from alloca at the beginning of splitBlock
            LoadInst load = new LoadInst(retAddr, splitBlock);
            splitBlock.getInstructions().removeLast(); // Remove load
            splitBlock.getInstructions().addFirst(load); // Add to front
            call.replaceAllUsesWith(load);
        }
        
        // 7. Remove Call
        call.remove();
        
        // 8. Insert Cloned Blocks into Function List (after callBlock)
        // Remove blocks automatically added by constructor to avoid duplicates
        caller.getBasicBlocks().remove(splitBlock);
        caller.getBasicBlocks().removeAll(result.blocks);

        int idx = caller.getBasicBlocks().indexOf(callBlock);
        caller.getBasicBlocks().addAll(idx + 1, result.blocks);
        caller.getBasicBlocks().add(idx + 1 + result.blocks.size(), splitBlock);
    }
}
