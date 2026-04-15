package midend.ir.values.instructions;

import midend.ir.types.IntType;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;

public class BinaryInst extends Instruction {
    public enum Operator {
        ADD("add"), SUB("sub"), MUL("mul"), SDIV("sdiv"), SREM("srem");
        
        private final String opStr;
        Operator(String s) { this.opStr = s; }
        @Override public String toString() { return opStr; }
    }

    private final Operator op;

    public BinaryInst(Operator op, Value v1, Value v2, BasicBlock parent) {
        super("%" + (parent.getParent().getAndUpdateCount()), IntType.I32, parent);
        this.op = op;
        addOperand(v1);
        addOperand(v2);
    }

    public Operator getOperator() { return op; }

    @Override
    public String toString() {
        Value v1 = getOperand(0);
        Value v2 = getOperand(1);
        return String.format("%s = %s i32 %s, %s", 
                getName(), op.toString(), v1.getName(), v2.getName());
    }
}
