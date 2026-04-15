package midend.ir.values.constants;

import midend.ir.types.ArrayType;
import midend.ir.types.Type;
import midend.ir.values.Constant;
import java.util.ArrayList;

public class ConstArray extends Constant {
    private ArrayList<Constant> elements;

    public ConstArray(ArrayType type, ArrayList<Constant> elements) {
        super(type, elements);
        this.elements = elements;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < elements.size(); i++) {
            sb.append(elements.get(i).getType().toString());
            sb.append(" ");
            sb.append(elements.get(i).toString());
            if (i < elements.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
