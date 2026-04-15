package midend.optim;

import midend.ir.values.Value;
import midend.ir.values.constants.ConstInt;
import midend.ir.values.instructions.*;

public class InstructionSimplify {
    
    public static Value simplify(Instruction instruction) {
        if (instruction instanceof BinaryInst) {
            BinaryInst bin = (BinaryInst) instruction;
            switch (bin.getOperator()) {
                case ADD: return simplifyAdd(bin);
                case SUB: return simplifySub(bin);
                case MUL: return simplifyMul(bin);
                case SDIV: return simplifySdiv(bin);
                case SREM: return simplifySrem(bin);
                default: return instruction;
            }
        } else if (instruction instanceof IcmpInst) {
            return simplifyIcmp((IcmpInst) instruction);
        } else if (instruction instanceof ZextInst) {
            return simplifyZext((ZextInst) instruction);
        }
        return instruction;
    }

    private static Value foldConstantInt(Instruction inst, Value lhs, Value rhs) {
        if (lhs instanceof ConstInt && rhs instanceof ConstInt) {
            int v1 = ((ConstInt) lhs).getVal();
            int v2 = ((ConstInt) rhs).getVal();
            int res = 0;
            
            if (inst instanceof BinaryInst) {
                switch (((BinaryInst) inst).getOperator()) {
                    case ADD: res = v1 + v2; break;
                    case SUB: res = v1 - v2; break;
                    case MUL: res = v1 * v2; break;
                    case SDIV: 
                        if (v2 == 0) return inst; 
                        res = v1 / v2; 
                        break;
                    case SREM: 
                        if (v2 == 0) return inst;
                        res = v1 % v2; 
                        break;
                    default: return null;
                }
                return new ConstInt(res);
            } else if (inst instanceof IcmpInst) {
                boolean cond = false;
                switch (((IcmpInst) inst).getPredicate()) {
                    case EQ: cond = (v1 == v2); break;
                    case NE: cond = (v1 != v2); break;
                    case SGT: cond = (v1 > v2); break;
                    case SGE: cond = (v1 >= v2); break;
                    case SLT: cond = (v1 < v2); break;
                    case SLE: cond = (v1 <= v2); break;
                }
                return new ConstInt(cond ? 1 : 0);
            }
        }
        return null;
    }

    private static Value simplifyAdd(BinaryInst inst) {
        Value lhs = inst.getOperand(0);
        Value rhs = inst.getOperand(1);
        
        Value folded = foldConstantInt(inst, lhs, rhs);
        if (folded != null) return folded;

        // x + 0 = x
        if (rhs instanceof ConstInt && ((ConstInt) rhs).getVal() == 0) return lhs;
        if (lhs instanceof ConstInt && ((ConstInt) lhs).getVal() == 0) return rhs;
        
        return inst;
    }

    private static Value simplifySub(BinaryInst inst) {
        Value lhs = inst.getOperand(0);
        Value rhs = inst.getOperand(1);
        
        Value folded = foldConstantInt(inst, lhs, rhs);
        if (folded != null) return folded;

        // x - 0 = x
        if (rhs instanceof ConstInt && ((ConstInt) rhs).getVal() == 0) return lhs;
        
        // x - x = 0
        if (lhs == rhs) return ConstInt.ZERO;
        
        return inst;
    }

    private static Value simplifyMul(BinaryInst inst) {
        Value lhs = inst.getOperand(0);
        Value rhs = inst.getOperand(1);
        
        Value folded = foldConstantInt(inst, lhs, rhs);
        if (folded != null) return folded;

        // x * 0 = 0
        if (rhs instanceof ConstInt && ((ConstInt) rhs).getVal() == 0) return ConstInt.ZERO;
        if (lhs instanceof ConstInt && ((ConstInt) lhs).getVal() == 0) return ConstInt.ZERO;

        // x * 1 = x
        if (rhs instanceof ConstInt && ((ConstInt) rhs).getVal() == 1) return lhs;
        if (lhs instanceof ConstInt && ((ConstInt) lhs).getVal() == 1) return rhs;

        return inst;
    }

    private static Value simplifySdiv(BinaryInst inst) {
        Value lhs = inst.getOperand(0);
        Value rhs = inst.getOperand(1);
        
        Value folded = foldConstantInt(inst, lhs, rhs);
        if (folded != null) return folded;

        // x / 1 = x
        if (rhs instanceof ConstInt && ((ConstInt) rhs).getVal() == 1) return lhs;
        
        // x / x = 1
        if (lhs == rhs) return new ConstInt(1);

        return inst;
    }

    private static Value simplifySrem(BinaryInst inst) {
        Value lhs = inst.getOperand(0);
        Value rhs = inst.getOperand(1);
        
        Value folded = foldConstantInt(inst, lhs, rhs);
        if (folded != null) return folded;

        // x % 1 = 0
        if (rhs instanceof ConstInt && ((ConstInt) rhs).getVal() == 1) return ConstInt.ZERO;
        
        // x % x = 0
        if (lhs == rhs) return ConstInt.ZERO;

        return inst;
    }

    private static Value simplifyIcmp(IcmpInst inst) {
        Value lhs = inst.getOperand(0);
        Value rhs = inst.getOperand(1);
        
        Value folded = foldConstantInt(inst, lhs, rhs);
        if (folded != null) return folded;
        
        if (lhs == rhs && inst.getPredicate() == IcmpInst.Predicate.EQ) return new ConstInt(1);
        if (lhs == rhs && inst.getPredicate() == IcmpInst.Predicate.NE) return ConstInt.ZERO;

        return inst;
    }

    private static Value simplifyZext(ZextInst inst) {
        Value src = inst.getOperand(0);
        if (src instanceof ConstInt) {
            return new ConstInt(((ConstInt) src).getVal());
        }
        return inst;
    }
}
