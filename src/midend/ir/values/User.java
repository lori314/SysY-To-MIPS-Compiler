package midend.ir.values;

import midend.ir.types.Type;
import java.util.ArrayList;

public abstract class User extends Value {
    protected ArrayList<Value> operands = new ArrayList<>();

    public User(String name, Type type) {
        super(name, type);
    }

    public void addOperand(Value v) {
        operands.add(v);
        // 维护 Def-Use 链：告诉 v，我使用了它
        if (v != null) {
            v.addUse(new Use(this, v));
        }
    }
    
    // 设置第 i 个操作数
    public void setOperand(int i, Value v) {
        if (i >= operands.size()) return;
        Value oldV = operands.get(i);
        if (oldV != null) oldV.removeUse(this); // 解除旧引用
        
        operands.set(i, v);
        if (v != null) v.addUse(new Use(this, v)); // 建立新引用
    }
    
    public Value getOperand(int i) {
        return operands.get(i);
    }
    
    public int getNumOperands() { return operands.size(); }

    // 将本指令中的 from 替换为 to
    public void replaceUsesOfWith(Value from, Value to) {
        for (int i = 0; i < operands.size(); i++) {
            if (operands.get(i) == from) {
                setOperand(i, to);
            }
        }
    }
}
