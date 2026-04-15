package midend.ir.values.constants;

import midend.ir.types.IntType;
import midend.ir.values.Constant;

public class ConstInt extends Constant {
    public static final ConstInt ZERO = new ConstInt(0);
    private final int val;

    public ConstInt(int val) {
        super(IntType.I32, val);
        this.val = val;
    }
    
    public int getVal() { return val; }

    @Override
    public String toString() {
        return String.valueOf(val);
    }
}
