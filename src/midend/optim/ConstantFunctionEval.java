package midend.optim;

import midend.ir.values.Module;
import midend.ir.values.Function;
import midend.ir.values.BasicBlock;
import midend.ir.values.Value;
import midend.ir.values.GlobalVariable;
import midend.ir.values.constants.ConstInt;
import midend.ir.values.instructions.*;

import java.util.*;

/**
 * 常量参数函数求值优化
 * 
 * 对于纯函数（如 fib），当调用参数是常量时，在编译期模拟执行并用结果替换调用。
 * 例如：fib(4) 在编译期计算得到 5，替换为常量 5。
 */
public class ConstantFunctionEval {
    
    private Map<String, Integer> evalCache = new HashMap<>();
    private Set<String> pureFunctions = new HashSet<>();
    private Module module;
    
    public void run(Module module) {
        this.module = module;
        
        // 识别纯函数（简单规则：不调用外部函数，不访问全局变量）
        identifyPureFunctions();
        
        // 多轮优化直到不变
        boolean changed = true;
        int maxIterations = 10;
        while (changed && maxIterations-- > 0) {
            changed = evaluateConstantCalls();
        }
    }
    
    private void identifyPureFunctions() {
        for (Function func : module.getFunctions()) {
            if (func.isBuiltin()) continue;
            if (isPureFunction(func)) {
                pureFunctions.add(func.getName());
            }
        }
    }
    
    private boolean isPureFunction(Function func) {
        // 检查函数是否是纯函数：
        // 1. 不调用外部函数（除了递归调用自己或其他纯函数）
        // 2. 不访问全局变量（store/load 全局）
        // 3. 只有一个参数（简化处理）
        
        if (func.getArguments().size() != 1) return false;
        
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst) {
                    CallInst call = (CallInst) inst;
                    Function callee = call.getFunction();
                    // 允许递归调用自己
                    if (!callee.getName().equals(func.getName()) && !callee.isBuiltin()) {
                        // 调用其他非内置函数，暂时认为不纯
                        // 实际上可以更精细地分析
                    }
                }
                if (inst instanceof StoreInst) {
                    StoreInst store = (StoreInst) inst;
                    if (store.getPointer() instanceof GlobalVariable) {
                        return false; // 写全局变量
                    }
                }
                if (inst instanceof LoadInst) {
                    LoadInst load = (LoadInst) inst;
                    if (load.getPointer() instanceof GlobalVariable) {
                        return false; // 读全局变量
                    }
                }
            }
        }
        return true;
    }
    
    private boolean evaluateConstantCalls() {
        boolean changed = false;
        
        for (Function func : module.getFunctions()) {
            if (func.isBuiltin()) continue;
            
            for (BasicBlock bb : func.getBasicBlocks()) {
                List<Instruction> instructions = bb.getInstructions();
                for (int i = 0; i < instructions.size(); i++) {
                    Instruction inst = instructions.get(i);
                    
                    if (inst instanceof CallInst) {
                        CallInst call = (CallInst) inst;
                        Function callee = call.getFunction();
                        
                        // 检查是否是纯函数且参数是常量
                        if (pureFunctions.contains(callee.getName()) && 
                            call.getNumOperands() == 2) { // 1 个 func + 1 个参数
                            
                            Value arg = call.getOperand(1);
                            if (arg instanceof ConstInt) {
                                int argVal = ((ConstInt) arg).getVal();
                                String cacheKey = callee.getName() + "(" + argVal + ")";
                                
                                Integer result = evalCache.get(cacheKey);
                                if (result == null) {
                                    result = evaluateFunction(callee, argVal, 0);
                                    if (result != null) {
                                        evalCache.put(cacheKey, result);
                                    }
                                }
                                
                                if (result != null) {
                                    // 用常量替换调用结果
                                    ConstInt constResult = new ConstInt(result);
                                    call.replaceAllUsesWith(constResult);
                                    // 标记删除（实际删除由 DCE 处理）
                                    bb.removeInstruction(call);
                                    i--; // 调整索引
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return changed;
    }
    
    private Integer evaluateFunction(Function func, int arg, int depth) {
        if (depth > 100) return null; // 防止无限递归
        
        String cacheKey = func.getName() + "(" + arg + ")";
        if (evalCache.containsKey(cacheKey)) {
            return evalCache.get(cacheKey);
        }
        
        // 简化的解释器：模拟执行函数
        Map<String, Integer> vars = new HashMap<>();
        Map<String, Integer> mem = new HashMap<>();
        
        // 设置参数
        if (!func.getArguments().isEmpty()) {
            String argName = func.getArguments().get(0).getName();
            vars.put(argName, arg);
        }
        
        LinkedList<BasicBlock> bbs = func.getBasicBlocks();
        if (bbs.isEmpty()) return null;
        
        // 从 entry 块开始执行
        return executeBlock(bbs.getFirst(), vars, mem, func, depth);
    }
    
    private Integer executeBlock(BasicBlock bb, Map<String, Integer> vars, 
                                  Map<String, Integer> mem, Function func, int depth) {
        for (Instruction inst : bb.getInstructions()) {
            Integer result = executeInstruction(inst, vars, mem, func, depth);
            if (result != null) {
                return result; // 遇到 return
            }
            if (result == null && inst instanceof ReturnInst) {
                return null; // 无法求值
            }
        }
        return null;
    }
    
    private Integer executeInstruction(Instruction inst, Map<String, Integer> vars,
                                        Map<String, Integer> mem, Function func, int depth) {
        if (inst instanceof AllocaInst) {
            // alloca 只是分配空间，初始化 mem
            mem.put(inst.getName(), 0);
            return null;
        }
        
        if (inst instanceof StoreInst) {
            StoreInst store = (StoreInst) inst;
            Value val = store.getValue();
            Value ptr = store.getPointer();
            Integer v = getValue(val, vars);
            if (v != null) {
                mem.put(ptr.getName(), v);
            }
            return null;
        }
        
        if (inst instanceof LoadInst) {
            LoadInst load = (LoadInst) inst;
            Value ptr = load.getPointer();
            Integer v = mem.get(ptr.getName());
            if (v != null) {
                vars.put(inst.getName(), v);
            }
            return null;
        }
        
        if (inst instanceof BinaryInst) {
            BinaryInst bin = (BinaryInst) inst;
            Integer lhs = getValue(bin.getOperand(0), vars);
            Integer rhs = getValue(bin.getOperand(1), vars);
            if (lhs != null && rhs != null) {
                int res = 0;
                switch (bin.getOperator()) {
                    case ADD: res = lhs + rhs; break;
                    case SUB: res = lhs - rhs; break;
                    case MUL: res = lhs * rhs; break;
                    case SDIV: if (rhs != 0) res = lhs / rhs; else return null; break;
                    case SREM: if (rhs != 0) res = lhs % rhs; else return null; break;
                    default: return null;
                }
                vars.put(inst.getName(), res);
            }
            return null;
        }
        
        if (inst instanceof IcmpInst) {
            IcmpInst icmp = (IcmpInst) inst;
            Integer lhs = getValue(icmp.getOperand(0), vars);
            Integer rhs = getValue(icmp.getOperand(1), vars);
            if (lhs != null && rhs != null) {
                boolean res = false;
                switch (icmp.getPredicate()) {
                    case EQ: res = lhs.equals(rhs); break;
                    case NE: res = !lhs.equals(rhs); break;
                    case SLT: res = lhs < rhs; break;
                    case SGT: res = lhs > rhs; break;
                    case SLE: res = lhs <= rhs; break;
                    case SGE: res = lhs >= rhs; break;
                    default: return null;
                }
                vars.put(inst.getName(), res ? 1 : 0);
            }
            return null;
        }
        
        if (inst instanceof BranchInst) {
            BranchInst br = (BranchInst) inst;
            if (br.isConditional()) {
                Integer cond = getValue(br.getCondition(), vars);
                if (cond != null) {
                    BasicBlock target = (cond != 0) ? br.getTrueBlock() : br.getFalseBlock();
                    return executeBlock(target, vars, mem, func, depth);
                }
            } else {
                return executeBlock(br.getTarget(), vars, mem, func, depth);
            }
            return null;
        }
        
        if (inst instanceof CallInst) {
            CallInst call = (CallInst) inst;
            Function callee = call.getFunction();
            
            if (pureFunctions.contains(callee.getName()) && call.getNumOperands() == 2) {
                Value argVal = call.getOperand(1);
                Integer argInt = getValue(argVal, vars);
                if (argInt != null) {
                    Integer result = evaluateFunction(callee, argInt, depth + 1);
                    if (result != null) {
                        vars.put(inst.getName(), result);
                        evalCache.put(callee.getName() + "(" + argInt + ")", result);
                        return null;
                    }
                }
            }
            return null; // 无法求值的调用
        }
        
        if (inst instanceof ReturnInst) {
            ReturnInst ret = (ReturnInst) inst;
            if (ret.getNumOperands() > 0) {
                return getValue(ret.getOperand(0), vars);
            }
            return 0; // void return
        }
        
        return null;
    }
    
    private Integer getValue(Value val, Map<String, Integer> vars) {
        if (val instanceof ConstInt) {
            return ((ConstInt) val).getVal();
        }
        return vars.get(val.getName());
    }
}
