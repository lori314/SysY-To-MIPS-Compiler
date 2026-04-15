package midend.ir.values;

import java.util.ArrayList;

public class Module {
    private final ArrayList<GlobalVariable> globalVariables = new ArrayList<>();
    private final ArrayList<Function> functions = new ArrayList<>();
    
    public void addGlobalVariable(GlobalVariable g) { globalVariables.add(g); }
    public void addFunction(Function f) { functions.add(f); }
    
    public ArrayList<Function> getFunctions() { return functions; }
    public ArrayList<GlobalVariable> getGlobalVariables() { return globalVariables; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("declare i32 @getint(...)\n");
        sb.append("declare i32 @printf(i8*, ...)\n\n");
        for (GlobalVariable g : globalVariables) sb.append(g).append("\n");
        sb.append("\n");
        for (Function f : functions) sb.append(f).append("\n");
        return sb.toString();
    }
}
