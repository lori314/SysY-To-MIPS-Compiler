package midend;

import ast.*;
import midend.ir.types.*;
import midend.ir.values.*;
import midend.ir.values.constants.ConstInt;
import midend.ir.values.constants.ConstArray;
import midend.ir.values.instructions.*;
import midend.ir.values.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class IRBuilder {
    private final Module module = new Module();
    private Function currentFunction = null;
    private BasicBlock currentBB = null;
    
    private final Stack<HashMap<String, Value>> symbolStack = new Stack<>();
    private final Stack<HashMap<String, Integer>> constIntStack = new Stack<>();
    private final Stack<HashMap<String, ArrayList<Integer>>> constArrayStack = new Stack<>();

    private final Stack<BasicBlock> loopContinueTarget = new Stack<>();
    private final Stack<BasicBlock> loopBreakTarget = new Stack<>();

    private Function getintFunc;
    private Function printfFunc;

    public IRBuilder() {
        initLibraryFunctions();
        // Initialize stacks before entering scope
        // enterScope will push the first layer
        enterScope();
    }

    private void initLibraryFunctions() {
        getintFunc = new Function("@getint", new FunctionType(IntType.I32, new ArrayList<>()), true);
        module.addFunction(getintFunc);
        
        printfFunc = new Function("@printf", new FunctionType(IntType.I32, new ArrayList<>()), true);
        module.addFunction(printfFunc);
    }

    public Module getModule() { return module; }

    private void enterScope() { 
        symbolStack.push(new HashMap<>()); 
        constIntStack.push(new HashMap<>());
        constArrayStack.push(new HashMap<>());
    }
    private void exitScope() { 
        symbolStack.pop(); 
        constIntStack.pop();
        constArrayStack.pop();
    }
    
    private void addSymbol(String name, Value val) {
        symbolStack.peek().put(name, val);
    }
    
    private Value findSymbol(String name) {
        for (int i = symbolStack.size() - 1; i >= 0; i--) {
            Value v = symbolStack.get(i).get(name);
            if (v != null) return v;
        }
        return null;
    }

    public Module visit(CompUnit node) {
        for (Decl decl : node.getDecls()) {
            visitDecl(decl, true);
        }
        for (FuncDef func : node.getFuncDefs()) {
            visitFuncDef(func);
        }
        if (node.getMainFuncDef() != null) {
            visitMainFunc(node.getMainFuncDef());
        }
        return module;
    }
    
    private boolean isBlockTerminated(BasicBlock bb) {
        if (bb.getInstructions().isEmpty()) return false;
        Instruction last = bb.getInstructions().getLast();
        return last.isTerminator();
    }

    private void visitFuncDef(FuncDef node) {
        String name = "@" + node.getIdent().getName();
        Type returnType = node.getFuncType().isVoid() ? VoidType.voidType : IntType.I32;
        
        ArrayList<Type> paramTypes = new ArrayList<>();
        if (node.getFuncFParams() != null) {
            for (FuncFParam param : node.getFuncFParams().getAllParams()) {
                if (param.isArray()) {
                    paramTypes.add(new PointerType(IntType.I32));
                } else {
                    paramTypes.add(IntType.I32);
                }
            }
        }

        FunctionType ft = new FunctionType(returnType, paramTypes);
        Function func = new Function(name, ft, false);
        module.addFunction(func);
        currentFunction = func;
        
        enterScope();
        currentBB = new BasicBlock("%entry", func);
        
        if (node.getFuncFParams() != null) {
            int argIdx = 0;
            for (FuncFParam param : node.getFuncFParams().getAllParams()) {
                Argument arg = new Argument("%arg_" + argIdx, paramTypes.get(argIdx), func);
                func.addArgument(arg);
                
                String paramName = param.getIdent().getName();
                AllocaInst alloca = new AllocaInst(arg.getType(), currentBB);
                new StoreInst(arg, alloca, currentBB);
                addSymbol(paramName, alloca);
                argIdx++;
            }
        }
        
        visitBlock(node.getBlock());
        
        if (!isBlockTerminated(currentBB)) {
            if (returnType.isVoid()) new ReturnInst(currentBB);
            else new ReturnInst(ConstInt.ZERO, currentBB);
        }
        
        exitScope();
    }
    
    private void visitMainFunc(MainFuncDef node) {
        FunctionType ft = new FunctionType(IntType.I32, new ArrayList<>());
        Function func = new Function("@main", ft, false);
        module.addFunction(func);
        currentFunction = func;
        
        enterScope();
        currentBB = new BasicBlock("%entry", func);
        visitBlock(node.getBlock());
        if (!isBlockTerminated(currentBB)) {
            new ReturnInst(ConstInt.ZERO, currentBB);
        }
        exitScope();
    }

    private Integer getConstInt(String name) {
        for (int i = constIntStack.size() - 1; i >= 0; i--) {
            if (constIntStack.get(i).containsKey(name)) {
                return constIntStack.get(i).get(name);
            }
        }
        return null;
    }
    
    private Integer getConstArrayVal(String name, int index) {
         for (int i = constArrayStack.size() - 1; i >= 0; i--) {
            if (constArrayStack.get(i).containsKey(name)) {
                ArrayList<Integer> arr = constArrayStack.get(i).get(name);
                if (index >= 0 && index < arr.size()) return arr.get(index);
            }
        }
        return null;
    }

    private int evalConstExp(Exp exp) {
        return evalAddExp(exp.getAddExp());
    }

    private int evalConstExp(ConstExp exp) {
        return evalAddExp(exp.getAddExp());
    }

    private int evalAddExp(AddExp exp) {
        if (exp.getLeft() == null) return evalMulExp(exp.getRight());
        int l = evalAddExp(exp.getLeft());
        int r = evalMulExp(exp.getRight());
        if (exp.getOp().getToken().getValue().equals("+")) return l + r;
        return l - r;
    }

    private int evalMulExp(MulExp exp) {
        if (exp.getLeft() == null) return evalUnaryExp(exp.getRight());
        int l = evalMulExp(exp.getLeft());
        int r = evalUnaryExp(exp.getRight());
        String op = exp.getOp().getToken().getValue();
        if (op.equals("*")) return l * r;
        if (op.equals("/")) return l / r;
        return l % r;
    }

    private int evalUnaryExp(UnaryExp exp) {
        if (exp.getType() == UnaryExp.Type.PRIMARY) return evalPrimaryExp(exp.getPrimaryExp());
        if (exp.getType() == UnaryExp.Type.OP_EXP) {
            int val = evalUnaryExp(exp.getUnaryExp());
            String op = exp.getUnaryOp().getOpToken().getToken().getValue();
            if (op.equals("+")) return val;
            if (op.equals("-")) return -val;
            return (val == 0) ? 1 : 0; 
        }
        return 0;
    }

    private int evalPrimaryExp(PrimaryExp exp) {
        if (exp.getType() == PrimaryExp.Type.NUMBER) return Integer.parseInt(exp.getNumber().getIntConst().getToken().getValue());
        if (exp.getType() == PrimaryExp.Type.EXP) return evalConstExp(exp.getExp());
        if (exp.getType() == PrimaryExp.Type.LVAL) {
            LVal lval = exp.getLVal();
            String name = lval.getIdent().getName();
            if (lval.getExp() == null) {
                Integer val = getConstInt(name);
                return (val != null) ? val : 0;
            } else {
                int idx = evalConstExp(lval.getExp());
                Integer val = getConstArrayVal(name, idx);
                return (val != null) ? val : 0;
            }
        }
        return 0;
    }

    private void visitDecl(Decl node, boolean isGlobal) {
        if (node instanceof ConstDecl) {
            visitConstDecl((ConstDecl) node, isGlobal);
        } else if (node instanceof VarDecl) {
            visitVarDecl((VarDecl) node, isGlobal);
        }
    }

    private int staticCounter = 0;

    private void visitConstDecl(ConstDecl node, boolean isGlobal) {
        ArrayList<ConstDef> allDefs = new ArrayList<>();
        allDefs.add(node.getConstDef());
        if (node.getConstDefs() != null) allDefs.addAll(node.getConstDefs());

        for (ConstDef def : allDefs) {
            String name = def.getIdent().getName();
            boolean isArray = def.isArray();
            
            if (isArray) {
                int size = evalConstExp(def.getConstExp());
                ArrayList<Integer> values = new ArrayList<>();
                ArrayList<Constant> irValues = new ArrayList<>();
                
                if (def.getConstInitVal().getConstExps() != null) {
                    for (ConstExp ce : def.getConstInitVal().getConstExps()) {
                        int v = evalConstExp(ce);
                        values.add(v);
                        irValues.add(new ConstInt(v));
                    }
                }
                while (values.size() < size) {
                    values.add(0);
                    irValues.add(new ConstInt(0));
                }
                
                constArrayStack.peek().put(name, values);
                
                ArrayType arrType = new ArrayType(IntType.I32, size);
                if (isGlobal) {
                    GlobalVariable gVar = new GlobalVariable("@" + name, new PointerType(arrType), true);
                    gVar.setInitializer(new ConstArray(arrType, irValues));
                    module.addGlobalVariable(gVar);
                    addSymbol(name, gVar);
                } else {
                    AllocaInst alloca = new AllocaInst(arrType, currentBB);
                    addSymbol(name, alloca);
                    for (int i = 0; i < size; i++) {
                        Value idx = new ConstInt(i);
                        Value ptr = new GepInst(alloca, currentBB);
                        ((GepInst)ptr).addIndex(new ConstInt(0));
                        ((GepInst)ptr).addIndex(idx);
                        new StoreInst(irValues.get(i), ptr, currentBB);
                    }
                }
            } else {
                int val = evalConstExp(def.getConstInitVal().getConstExp());
                constIntStack.peek().put(name, val);
                
                if (isGlobal) {
                    GlobalVariable gVar = new GlobalVariable("@" + name, new PointerType(IntType.I32), true);
                    gVar.setInitializer(new ConstInt(val));
                    module.addGlobalVariable(gVar);
                    addSymbol(name, gVar);
                } else {
                    AllocaInst alloca = new AllocaInst(IntType.I32, currentBB);
                    addSymbol(name, alloca);
                    new StoreInst(new ConstInt(val), alloca, currentBB);
                }
            }
        }
    }

    private void visitVarDecl(VarDecl node, boolean isGlobal) {
        boolean isStatic = (node.getStaticTy() != null);
        
        ArrayList<VarDef> allDefs = new ArrayList<>();
        allDefs.add(node.getVarDef());
        if (node.getVarDefs() != null) allDefs.addAll(node.getVarDefs());

        for (VarDef def : allDefs) {
            String name = def.getIdent().getName();
            boolean isArray = (def.getConstExp() != null);
            
            if (isArray) {
                int size = evalConstExp(def.getConstExp());
                ArrayType arrType = new ArrayType(IntType.I32, size);
                
                if (isGlobal || isStatic) {
                    String gName = isGlobal ? "@" + name : "@" + currentFunction.getName().substring(1) + "." + name + "." + (staticCounter++);
                    GlobalVariable gVar = new GlobalVariable(gName, new PointerType(arrType), false);
                    
                    ArrayList<Constant> irValues = new ArrayList<>();
                    if (def.getInitVal() != null && def.getInitVal().getExps() != null) {
                        for (Exp e : def.getInitVal().getExps()) {
                            int v = evalConstExp(e);
                            irValues.add(new ConstInt(v));
                        }
                    }
                    while (irValues.size() < size) irValues.add(new ConstInt(0));
                    
                    gVar.setInitializer(new ConstArray(arrType, irValues));
                    module.addGlobalVariable(gVar);
                    addSymbol(name, gVar);
                } else {
                    AllocaInst alloca = new AllocaInst(arrType, currentBB);
                    addSymbol(name, alloca);
                    if (def.getInitVal() != null && def.getInitVal().getExps() != null) {
                        int i = 0;
                        for (Exp e : def.getInitVal().getExps()) {
                            Value val = visitExp(e);
                            Value idx = new ConstInt(i++);
                            Value ptr = new GepInst(alloca, currentBB);
                            ((GepInst)ptr).addIndex(new ConstInt(0));
                            ((GepInst)ptr).addIndex(idx);
                            new StoreInst(val, ptr, currentBB);
                        }
                        while (i < size) {
                            Value idx = new ConstInt(i++);
                            Value ptr = new GepInst(alloca, currentBB);
                            ((GepInst)ptr).addIndex(new ConstInt(0));
                            ((GepInst)ptr).addIndex(idx);
                            new StoreInst(new ConstInt(0), ptr, currentBB);
                        }
                    }
                }
            } else {
                if (isGlobal || isStatic) {
                    String gName = isGlobal ? "@" + name : "@" + currentFunction.getName().substring(1) + "." + name + "." + (staticCounter++);
                    GlobalVariable gVar = new GlobalVariable(gName, new PointerType(IntType.I32), false);
                    int val = 0;
                    if (def.getInitVal() != null && def.getInitVal().getExp() != null) {
                        val = evalConstExp(def.getInitVal().getExp());
                    }
                    gVar.setInitializer(new ConstInt(val));
                    module.addGlobalVariable(gVar);
                    addSymbol(name, gVar);
                } else {
                    AllocaInst alloca = new AllocaInst(IntType.I32, currentBB);
                    addSymbol(name, alloca);
                    if (def.getInitVal() != null && def.getInitVal().getExp() != null) {
                        Value val = visitExp(def.getInitVal().getExp());
                        new StoreInst(val, alloca, currentBB);
                    }
                }
            }
        }
    }

    private void visitBlock(Block node) {
        for (BlockItem item : node.getBlockItems()) {
            if (item instanceof Decl) {
                visitDecl((Decl) item, false);
            } else {
                visitStmt((Stmt) item);
            }
            if (isBlockTerminated(currentBB)) break;
        }
    }

    private void visitStmt(Stmt node) {
        switch (node.getType()) {
            case ReturnStmt:
                if (node.getExp() != null) {
                    Value val = visitExp(node.getExp());
                    new ReturnInst(val, currentBB);
                } else {
                    new ReturnInst(currentBB);
                }
                break;
            case AssignStmt:
                Value rhs = visitExp(node.getExp());
                Value lhsPtr = visitLValPtr(node.getLVal());
                new StoreInst(rhs, lhsPtr, currentBB);
                break;
            case IfStmt:
                visitIfStmt(node);
                break;
            case ForStmt:
                visitLoopStmt(node);
                break;
            case BreakStmt:
                if (!loopBreakTarget.isEmpty())
                    new BranchInst(loopBreakTarget.peek(), currentBB);
                break;
            case ContinueStmt:
                if (!loopContinueTarget.isEmpty())
                    new BranchInst(loopContinueTarget.peek(), currentBB);
                break;
            case ExpStmt:
                if (node.getExp() != null) visitExp(node.getExp());
                break;
            case PrintfStmt:
                visitPrintfStmt(node);
                break;
            case BlockStmt:
                enterScope();
                visitBlock(node.getBlock());
                exitScope();
                break;

            default:
                break;
        }
    }

    private int blockCounter = 0;
    private String getNextBlockName(String prefix) {
        return prefix + "_" + (blockCounter++);
    }

    private void visitIfStmt(Stmt node) {
        BasicBlock trueBB = new BasicBlock(getNextBlockName("%if_true"), currentFunction);
        BasicBlock falseBB = (node.getElseStmt() != null) ? new BasicBlock(getNextBlockName("%if_false"), currentFunction) : null;
        BasicBlock nextBB = new BasicBlock(getNextBlockName("%if_next"), currentFunction);
        
        BasicBlock falseTarget = (falseBB != null) ? falseBB : nextBB;
        visitCond(node.getCond(), trueBB, falseTarget);
        
        currentBB = trueBB;
        visitStmt(node.getThenStmt());
        if (!isBlockTerminated(currentBB)) new BranchInst(nextBB, currentBB);
        
        if (falseBB != null) {
            currentBB = falseBB;
            visitStmt(node.getElseStmt());
            if (!isBlockTerminated(currentBB)) new BranchInst(nextBB, currentBB);
        }
        
        currentBB = nextBB;
    }

    private void visitLoopStmt(Stmt node) {
        if (node.getForStmt1() != null) {
            visitForStmt(node.getForStmt1());
        }

        BasicBlock condBB = new BasicBlock(getNextBlockName("%loop_cond"), currentFunction);
        BasicBlock bodyBB = new BasicBlock(getNextBlockName("%loop_body"), currentFunction);
        BasicBlock updateBB = new BasicBlock(getNextBlockName("%loop_update"), currentFunction);
        BasicBlock nextBB = new BasicBlock(getNextBlockName("%loop_next"), currentFunction);

        if (!isBlockTerminated(currentBB)) new BranchInst(condBB, currentBB);

        currentBB = condBB;
        if (node.getCond() != null) {
            visitCond(node.getCond(), bodyBB, nextBB);
        } else {
            new BranchInst(bodyBB, currentBB);
        }

        currentBB = bodyBB;
        loopContinueTarget.push(updateBB);
        loopBreakTarget.push(nextBB);
        
        if (node.getLoopBodyStmt() != null) {
            visitStmt(node.getLoopBodyStmt());
        }
        
        loopContinueTarget.pop();
        loopBreakTarget.pop();
        
        if (!isBlockTerminated(currentBB)) new BranchInst(updateBB, currentBB);

        currentBB = updateBB;
        if (node.getForStmt2() != null) {
            visitForStmt(node.getForStmt2());
        }
        if (!isBlockTerminated(currentBB)) new BranchInst(condBB, currentBB);

        currentBB = nextBB;
    }

    private void visitForStmt(ForStmt node) {
        Value rhs = visitExp(node.getFirstExp());
        Value lhsPtr = visitLValPtr(node.getFirstLVal());
        new StoreInst(rhs, lhsPtr, currentBB);
        
        if (node.getOtherLVals() != null) {
            for (int i = 0; i < node.getOtherLVals().size(); i++) {
                Value r = visitExp(node.getOtherExps().get(i));
                Value l = visitLValPtr(node.getOtherLVals().get(i));
                new StoreInst(r, l, currentBB);
            }
        }
    }

    private void visitPrintfStmt(Stmt node) {
        String format = node.getStringConst().getToken().getValue();
        if (format.startsWith("\"") && format.endsWith("\"")) {
            format = format.substring(1, format.length() - 1);
        }
        
        // Unescape string
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '\\' && i + 1 < format.length()) {
                char next = format.charAt(i + 1);
                if (next == 'n') {
                    sb.append('\n');
                    i++;
                } else if (next == '\\') {
                    sb.append('\\');
                    i++;
                } else if (next == '\"') {
                    sb.append('\"');
                    i++;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        format = sb.toString();
        
        midend.ir.values.constants.ConstString constStr = new midend.ir.values.constants.ConstString(format);
        String strName = "@.str" + module.getGlobalVariables().size();
        GlobalVariable strVar = new GlobalVariable(strName, new PointerType(constStr.getType()), true); 
        strVar.setInitializer(constStr);
        module.addGlobalVariable(strVar);
        
        GepInst gep = new GepInst(strVar, currentBB);
        gep.addIndex(ConstInt.ZERO);
        gep.addIndex(ConstInt.ZERO);
        
        ArrayList<Value> args = new ArrayList<>();
        args.add(gep);
        for (Exp exp : node.getPrintfExps()) {
            args.add(visitExp(exp));
        }

        CallInst call = new CallInst(printfFunc, currentBB);
        for (Value arg : args) {
            call.addArgument(arg);
        }
    }

    private Value visitExp(Exp node) {
        return visitAddExp(node.getAddExp());
    }

    private Value visitAddExp(AddExp node) {
        if (node.getLeft() == null) return visitMulExp(node.getRight());
        Value lhs = visitAddExp(node.getLeft());
        Value rhs = visitMulExp(node.getRight());
        BinaryInst.Operator op = (node.getOp().getType() == frontend.TokenType.PLUS) 
                                 ? BinaryInst.Operator.ADD : BinaryInst.Operator.SUB;
        return new BinaryInst(op, lhs, rhs, currentBB);
    }

    private Value visitMulExp(MulExp node) {
        if (node.getLeft() == null) return visitUnaryExp(node.getRight());
        Value lhs = visitMulExp(node.getLeft());
        Value rhs = visitUnaryExp(node.getRight());
        BinaryInst.Operator op;
        if (node.getOp().getType() == frontend.TokenType.MULT) op = BinaryInst.Operator.MUL;
        else if (node.getOp().getType() == frontend.TokenType.DIV) op = BinaryInst.Operator.SDIV;
        else op = BinaryInst.Operator.SREM;
        return new BinaryInst(op, lhs, rhs, currentBB);
    }

    private Value visitUnaryExp(UnaryExp node) {
        switch (node.getType()) {
            case PRIMARY:
                return visitPrimaryExp(node.getPrimaryExp());
            case OP_EXP:
                Value val = visitUnaryExp(node.getUnaryExp());
                frontend.TokenType opType = node.getUnaryOp().getOpToken().getToken().getType();
                if (opType == frontend.TokenType.MINU) {
                    return new BinaryInst(BinaryInst.Operator.SUB, ConstInt.ZERO, val, currentBB);
                } else if (opType == frontend.TokenType.NOT) {
                    return new IcmpInst(IcmpInst.Predicate.EQ, val, ConstInt.ZERO, currentBB);
                }
                return val;
            case FUNC_CALL:
                String funcName = "@" + node.getFuncName().getName();
                Function func = null;
                for (Function f : module.getFunctions()) {
                    if (f.getName().equals(funcName)) {
                        func = f;
                        break;
                    }
                }
                if (funcName.equals("@getint")) func = getintFunc;
                
                ArrayList<Value> args = new ArrayList<>();
                if (node.getFuncRParams() != null) {
                    for (Exp paramExp : node.getFuncRParams().getParamExps()) {
                        args.add(visitExp(paramExp));
                    }
                }

                CallInst call = new CallInst(func, currentBB);
                for (Value arg : args) {
                    call.addArgument(arg);
                }
                return call;
            default:
                return null;
        }
    }

    private Value visitPrimaryExp(PrimaryExp node) {
        if (node.getExp() != null) return visitExp(node.getExp());
        if (node.getNumber() != null) {
            String numStr = node.getNumber().getIntConst().getToken().getValue();
            return new ConstInt(Integer.parseInt(numStr));
        }
        if (node.getLVal() != null) return visitLValVal(node.getLVal());
        return null;
    }

    private Value visitLValPtr(LVal node) {
        String name = node.getIdent().getName();
        Value baseAddr = findSymbol(name);
        
        if (node.isArrayAccess()) {
            Value offset = visitExp(node.getExp());
            Type type = baseAddr.getType();
            if (type instanceof PointerType) {
                Type pointee = ((PointerType) type).getPointeeType();
                if (pointee instanceof ArrayType) {
                    GepInst gep = new GepInst(baseAddr, currentBB);
                    gep.addIndex(ConstInt.ZERO);
                    gep.addIndex(offset);
                    return gep;
                } else if (pointee instanceof PointerType) {
                    LoadInst ptr = new LoadInst(baseAddr, currentBB);
                    GepInst gep = new GepInst(ptr, currentBB);
                    gep.addIndex(offset);
                    return gep;
                } else {
                    GepInst gep = new GepInst(baseAddr, currentBB);
                    gep.addIndex(offset);
                    return gep;
                }
            }
        }
        return baseAddr;
    }

    private Value visitLValVal(LVal node) {
        Value ptr = visitLValPtr(node);
        Type type = ptr.getType();
        if (type instanceof PointerType) {
            Type pointee = ((PointerType) type).getPointeeType();
            if (pointee instanceof ArrayType && !node.isArrayAccess()) {
                GepInst gep = new GepInst(ptr, currentBB);
                gep.addIndex(ConstInt.ZERO);
                gep.addIndex(ConstInt.ZERO);
                return gep;
            }
        }
        return new LoadInst(ptr, currentBB);
    }

    private void visitCond(Cond node, BasicBlock trueTarget, BasicBlock falseTarget) {
        visitLOrExp(node.getLOrExp(), trueTarget, falseTarget);
    }

    private void visitLOrExp(LOrExp node, BasicBlock trueTarget, BasicBlock falseTarget) {
        if (node.getLeft() == null) {
            visitLAndExp(node.getRight(), trueTarget, falseTarget);
        } else {
            BasicBlock checkB = new BasicBlock(getNextBlockName("%or_check_b"), currentFunction);
            visitLOrExp(node.getLeft(), trueTarget, checkB);
            currentBB = checkB;
            visitLAndExp(node.getRight(), trueTarget, falseTarget);
        }
    }

    private void visitLAndExp(LAndExp node, BasicBlock trueTarget, BasicBlock falseTarget) {
        if (node.getLeft() == null) {
            visitEqExp(node.getRight(), trueTarget, falseTarget);
        } else {
            BasicBlock checkB = new BasicBlock(getNextBlockName("%and_check_b"), currentFunction);
            visitLAndExp(node.getLeft(), checkB, falseTarget);
            currentBB = checkB;
            visitEqExp(node.getRight(), trueTarget, falseTarget);
        }
    }

    private void visitEqExp(EqExp node, BasicBlock trueTarget, BasicBlock falseTarget) {
        Value val = visitEqExpVal(node);
        if (val.getType().isInt() && ((IntType)val.getType()).getBits() == 32) {
            val = new IcmpInst(IcmpInst.Predicate.NE, val, ConstInt.ZERO, currentBB);
        }
        new BranchInst(val, trueTarget, falseTarget, currentBB);
    }

    private Value visitEqExpVal(EqExp node) {
        if (node.getLeft() == null) return visitRelExpVal(node.getRight());
        Value lhs = visitEqExpVal(node.getLeft());
        Value rhs = visitRelExpVal(node.getRight());
        IcmpInst.Predicate pred = (node.getOp().getType() == frontend.TokenType.EQL) 
                                  ? IcmpInst.Predicate.EQ : IcmpInst.Predicate.NE;
        return new IcmpInst(pred, lhs, rhs, currentBB);
    }

    private Value visitRelExpVal(RelExp node) {
        if (node.getLeft() == null) return visitAddExp(node.getRight());
        Value lhs = visitRelExpVal(node.getLeft());
        Value rhs = visitAddExp(node.getRight());
        IcmpInst.Predicate pred = IcmpInst.Predicate.EQ;
        switch (node.getOp().getType()) {
            case LSS: pred = IcmpInst.Predicate.SLT; break;
            case LEQ: pred = IcmpInst.Predicate.SLE; break;
            case GRE: pred = IcmpInst.Predicate.SGT; break;
            case GEQ: pred = IcmpInst.Predicate.SGE; break;
            default: break;
        }
        return new IcmpInst(pred, lhs, rhs, currentBB);
    }
}
