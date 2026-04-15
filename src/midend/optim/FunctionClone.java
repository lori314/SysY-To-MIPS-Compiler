package midend.optim;

import midend.ir.types.FunctionType;
import midend.ir.types.PointerType;
import midend.ir.types.Type;
import midend.ir.values.*;
import midend.ir.values.constants.ConstInt;
import midend.ir.values.instructions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FunctionClone {
    private final Map<Value, Value> valueMap = new HashMap<>();
    private final Function newFunc; // If cloning to a new function (not inlining)
    private final Function targetFunc; // The function we are inserting into (for inlining)

    // For Inlining: targetFunc is the caller
    public FunctionClone(Function targetFunc) {
        this.targetFunc = targetFunc;
        this.newFunc = null;
    }

    // Clone a function body into the target function (Inlining)
    // Returns the entry block of the cloned body and the exit block (if unified) or list of return blocks
    public CloneResult cloneForInlining(Function srcFunc, ArrayList<Value> args, BasicBlock afterBlock, int suffix) {
        valueMap.clear();
        
        // 1. Map Arguments
        for (int i = 0; i < srcFunc.getArguments().size(); i++) {
            valueMap.put(srcFunc.getArguments().get(i), args.get(i));
        }

        // 2. Clone Basic Blocks
        ArrayList<BasicBlock> clonedBlocks = new ArrayList<>();
        for (BasicBlock srcBB : srcFunc.getBasicBlocks()) {
            // Create new block in target function
            // We append them for now, will fix order later
            BasicBlock newBB = new BasicBlock("%inline_" + srcBB.getName().replace("%", "") + "_" + suffix, targetFunc);
            valueMap.put(srcBB, newBB);
            clonedBlocks.add(newBB);
        }

        // 3. Clone Instructions
        ArrayList<ReturnInst> returns = new ArrayList<>();
        
        for (BasicBlock srcBB : srcFunc.getBasicBlocks()) {
            BasicBlock destBB = (BasicBlock) valueMap.get(srcBB);
            for (Instruction inst : srcBB.getInstructions()) {
                Instruction cloned = cloneInstruction(inst, destBB);
                valueMap.put(inst, cloned);
                if (cloned instanceof ReturnInst) {
                    returns.add((ReturnInst) cloned);
                }
            }
        }

        // 4. Fix Branch Targets
        for (BasicBlock srcBB : srcFunc.getBasicBlocks()) {
            BasicBlock destBB = (BasicBlock) valueMap.get(srcBB);
            for (Instruction inst : destBB.getInstructions()) {
                if (inst instanceof BranchInst) {
                    BranchInst br = (BranchInst) inst;
                    // BranchInst operands are [cond, trueBlock, falseBlock] or [targetBlock]
                    // But operands are Values. BasicBlock is a Value.
                    // We need to update them.
                    for (int i = 0; i < br.getNumOperands(); i++) {
                        Value op = br.getOperand(i);
                        if (op instanceof BasicBlock) {
                            // If it's a block in the cloned function, map it.
                            // If it's external (shouldn't happen for branches usually), keep it?
                            // But wait, cloneInstruction ALREADY mapped operands!
                            // Let's check cloneInstruction for BranchInst.
                            // dst = new BranchInst(map(s.getOperand(0)), (BasicBlock)map(s.getOperand(1)), ...);
                            // map() returns valueMap.get(v) if exists.
                            // When we cloned instructions, we iterated blocks.
                            // If the target block was NOT yet visited/created, map() would return the original block!
                            // Because we create blocks in step 2, map should have all blocks.
                            // Let's check step 2.
                            // Yes, we create all blocks and put them in valueMap.
                            // So map() should work correctly.
                            // Why did I add step 4?
                            // Maybe I thought map() wouldn't work if block is forward declared?
                            // But we create ALL blocks first.
                            // So step 4 might be redundant OR harmful if map() returns null?
                            // map() returns v if not found.
                            // If map() worked, then operands are already correct.
                            // Let's verify map().
                        }
                    }
                }
            }
        }

        return new CloneResult((BasicBlock) valueMap.get(srcFunc.getBasicBlocks().getFirst()), clonedBlocks, returns);
    }

    private Instruction cloneInstruction(Instruction src, BasicBlock parent) {
        // We use a generic cloning approach or specific?
        // Since we don't have a clone() method on Instruction, we construct new ones.
        // This is tedious but necessary.
        
        Instruction dst = null;
        
        if (src instanceof BinaryInst) {
            BinaryInst s = (BinaryInst) src;
            dst = new BinaryInst(s.getOperator(), map(s.getOperand(0)), map(s.getOperand(1)), parent);
        } else if (src instanceof IcmpInst) {
            IcmpInst s = (IcmpInst) src;
            dst = new IcmpInst(s.getPredicate(), map(s.getOperand(0)), map(s.getOperand(1)), parent);
        } else if (src instanceof BranchInst) {
            BranchInst s = (BranchInst) src;
            if (s.isConditional()) {
                dst = new BranchInst(map(s.getOperand(0)), (BasicBlock)map(s.getOperand(1)), (BasicBlock)map(s.getOperand(2)), parent);
            } else {
                dst = new BranchInst((BasicBlock)map(s.getOperand(0)), parent);
            }
        } else if (src instanceof CallInst) {
            CallInst s = (CallInst) src;
            dst = new CallInst(s.getFunction(), parent); // Function is global, no map needed usually
            for (int i = 1; i < s.getNumOperands(); i++) {
                ((CallInst) dst).addArgument(map(s.getOperand(i)));
            }
        } else if (src instanceof LoadInst) {
            LoadInst s = (LoadInst) src;
            dst = new LoadInst(map(s.getOperand(0)), parent);
        } else if (src instanceof StoreInst) {
            StoreInst s = (StoreInst) src;
            dst = new StoreInst(map(s.getOperand(0)), map(s.getOperand(1)), parent);
        } else if (src instanceof AllocaInst) {
            AllocaInst s = (AllocaInst) src;
            dst = new AllocaInst(s.getAllocatedType(), parent);
        } else if (src instanceof GepInst) {
            GepInst s = (GepInst) src;
            dst = new GepInst(map(s.getOperand(0)), parent);
            for (int i = 1; i < s.getNumOperands(); i++) {
                ((GepInst) dst).addIndex(map(s.getOperand(i)));
            }
        } else if (src instanceof ZextInst) {
            ZextInst s = (ZextInst) src;
            dst = new ZextInst(map(s.getOperand(0)), s.getType(), parent);
        } else if (src instanceof ReturnInst) {
            ReturnInst s = (ReturnInst) src;
            if (s.getNumOperands() > 0) {
                dst = new ReturnInst(map(s.getOperand(0)), parent);
            } else {
                dst = new ReturnInst(parent);
            }
        }
        
        return dst;
    }

    private Value map(Value v) {
        if (v instanceof ConstInt) return v;
        if (v instanceof Function) return v;
        if (v instanceof GlobalVariable) return v;
        if (valueMap.containsKey(v)) {
            Value mapped = valueMap.get(v);
            if (mapped == null) {
                // Should not happen if logic is correct
                return v;
            }
            return mapped;
        }
        return v; // Should be constant or global
    }

    public static class CloneResult {
        public BasicBlock entry;
        public ArrayList<BasicBlock> blocks;
        public ArrayList<ReturnInst> returns;

        public CloneResult(BasicBlock entry, ArrayList<BasicBlock> blocks, ArrayList<ReturnInst> returns) {
            this.entry = entry;
            this.blocks = blocks;
            this.returns = returns;
        }
    }
}
