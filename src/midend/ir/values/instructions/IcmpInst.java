package midend.ir.values.instructions;

import midend.ir.types.IntType;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;

public class IcmpInst extends Instruction {
    public enum Predicate {
        EQ("eq"), NE("ne"), SGT("sgt"), SGE("sge"), SLT("slt"), SLE("sle");
        
        private final String s;
        Predicate(String s) { this.s = s; }
        @Override public String toString() { return s; }
    }

    private final Predicate predicate;

    public IcmpInst(Predicate p, Value v1, Value v2, BasicBlock parent) {
        super("%" + (parent.getParent().getAndUpdateCount()), IntType.I1, parent); // 返回类型是 i1
        this.predicate = p;
        addOperand(v1);
        addOperand(v2);
    }

    public Predicate getPredicate() { return predicate; }

    @Override
    public String toString() {
        return String.format("%s = icmp %s i32 %s, %s", 
            getName(), predicate.toString(), getOperand(0).getName(), getOperand(1).getName());
    }
}
