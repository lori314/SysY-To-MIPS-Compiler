package midend.ir.values.constants;

import midend.ir.types.ArrayType;
import midend.ir.types.IntType;
import midend.ir.values.Constant;

public class ConstString extends Constant {
    private String content;

    public ConstString(String content) {
        super(new ArrayType(IntType.I8, content.length() + 1), content);
        this.content = content;
    }

    public String getContent() { return content; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("c\"");
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') sb.append("\\0A");
            else if (c == '\"') sb.append("\\22");
            else if (c == '\\') sb.append("\\5C");
            else sb.append(c);
        }
        sb.append("\\00\"");
        return sb.toString();
    }
}
