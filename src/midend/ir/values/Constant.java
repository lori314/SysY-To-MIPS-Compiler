package midend.ir.values;

import midend.ir.types.Type;

public class Constant extends Value {
    private final Object value;

    public Constant(Type type, Object value) {
        super(value.toString(), type);
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
