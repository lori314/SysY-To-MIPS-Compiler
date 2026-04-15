package midend.ir.values;

import midend.ir.types.Type;

public class Argument extends Value {
    private Function parent;

    public Argument(String name, Type type, Function parent) {
        super(name, type);
        this.parent = parent;
    }

    public Function getParent() { return parent; }

    @Override
    public String toString() {
        return type.toString() + " " + name;
    }
}
