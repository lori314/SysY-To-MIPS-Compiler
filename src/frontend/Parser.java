package frontend;

import ast.*;
import ast.Number;
import symbol.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final ArrayList<Token> tokens;
    private int position = 0;
    private Token currentToken;
    private final List<String> errors = new ArrayList<>();
    private CompUnit root;
    private final SymbolTableManager symbolTableManager = new SymbolTableManager();
    private int forBlockNum = 0;

    public Parser(List<Token> tokens) {
        this.tokens = new ArrayList<>(tokens);
        this.currentToken = tokens.get(0);
    }

    private Token advance() {
        Token oldToken = currentToken;
        if (position < tokens.size() - 1) {
            position++;
            currentToken = tokens.get(position);
        }
        return oldToken;
    }

    private void error(String message) {
        errors.add(message);
    }

    public void parse() {
        this.root = parseCompUnit();
    }

    public CompUnit getASTRoot() {
        return this.root;
    }

    public String getParseTreeString() {
        if (root == null) {
            return "Parsing has not been run or failed at the root.";
        }
        return root.toString();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public ArrayList<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public String getSymbolString() {
        return symbolTableManager.toString();
    }

    private boolean isDecl() {
        if (position + 2 >= tokens.size()) return false;
        if (currentToken.getType() == TokenType.CONSTTK) {
            return true;
        }
        if (currentToken.getType() == TokenType.STATICTK) {
            return true;
        }
        if (currentToken.getType() == TokenType.INTTK) {
            return tokens.get(position + 2).getType() != TokenType.LPARENT;
        }
        return false;
    }

    private boolean isFuncDef() {
        if (position + 2 >= tokens.size()) return false;
        if (currentToken.getType() == TokenType.VOIDTK) {
            return true;
        }
        if (currentToken.getType() == TokenType.INTTK) {
            return tokens.get(position + 1).getType() == TokenType.IDENFR;
        }
        return false;
    }

    private boolean isStartOfExp() {
        TokenType type = currentToken.getType();
        return type == TokenType.PLUS || type == TokenType.MINU || type == TokenType.IDENFR ||
                type == TokenType.LPARENT || type == TokenType.INTCON;
    }

    private CompUnit parseCompUnit() {
        ArrayList<Decl> decls = new ArrayList<>();
        ArrayList<FuncDef> funcs = new ArrayList<>();
        while (isDecl()) {
            decls.add(parseDecl());
        }
        while (isFuncDef()) {
            funcs.add(parseFuncDef());
        }
        MainFuncDef mainFunc = parseMainFuncDef();
        return new CompUnit(decls, funcs, mainFunc);
    }

    private Decl parseDecl() {
        if (currentToken.getType() == TokenType.CONSTTK) {
            return parseConstDecl();
        } else {
            return parseVarDecl();
        }
    }

    private ConstDecl parseConstDecl() {
        Terminal constTk = new Terminal(advance());
        BType bType = parseBType();
        ConstDef firstDef = parseConstDef();
        ArrayList<Terminal> commas = new ArrayList<>();
        ArrayList<ConstDef> otherDefs = new ArrayList<>();
        while (currentToken.getType() == TokenType.COMMA) {
            commas.add(new Terminal(advance()));
            otherDefs.add(parseConstDef());
        }
        Terminal semicn = null;
        if (currentToken.getType() == TokenType.SEMICN) {
            semicn = new Terminal(advance());
        } else {
            error(tokens.get(position - 1).getLineNumber() + " " + "i");
        }
        return new ConstDecl(constTk, bType, firstDef, commas, otherDefs, semicn);
    }

    private BType parseBType() {
        return new BType(new Terminal(advance()));
    }

    private ConstDef parseConstDef() {
        Ident ident = parseIdent();
        addSymbolFromDeclaration(true,false,ident.getIdentifierToken());
        Terminal lBrack = null;
        ConstExp constExp = null;
        Terminal rBrack = null;

        if (currentToken.getType() == TokenType.LBRACK) {
            lBrack = new Terminal(advance());
            constExp = parseConstExp();
            if (currentToken.getType() == TokenType.RBRACK) {
                rBrack = new Terminal(advance());
            } else {
                error(tokens.get(position - 1).getLineNumber() + " " + "k");
            }
        }

        Terminal assign = new Terminal(advance());
        ConstInitVal constInitVal = parseConstInitVal();

        return new ConstDef(ident, lBrack, constExp, rBrack, assign, constInitVal);
    }

    private Ident parseIdent() {
        return new Ident(new Terminal(advance()));
    }

    private ConstExp parseConstExp() {
        return new ConstExp(parseAddExp());
    }

    private AddExp parseAddExp() {
        MulExp mulExp = parseMulExp();
        AddExp left = new AddExp(mulExp);
        while (currentToken.getType() == TokenType.PLUS || currentToken.getType() == TokenType.MINU) {
            Terminal op = new Terminal(advance());
            MulExp right = parseMulExp();
            left = new AddExp(left, op, right);
        }
        return left;
    }

    private MulExp parseMulExp() {
        UnaryExp unaryExp = parseUnaryExp();
        MulExp left = new MulExp(unaryExp);
        while (currentToken.getType() == TokenType.MULT || currentToken.getType() == TokenType.DIV || currentToken.getType() == TokenType.MOD) {
            Terminal op = new Terminal(advance());
            UnaryExp right = parseUnaryExp();
            left = new MulExp(left, op, right);
        }
        return left;
    }

    private UnaryExp parseUnaryExp() {
        if (currentToken.getType() == TokenType.PLUS || currentToken.getType() == TokenType.MINU || currentToken.getType() == TokenType.NOT) {
            UnaryOp op = new UnaryOp(new Terminal(advance()));
            return new UnaryExp(op, parseUnaryExp());
        } else if (currentToken.getType() == TokenType.IDENFR && tokens.get(position + 1).getType() == TokenType.LPARENT) {
            Ident funcName = parseIdent();
            SymbolEntry funcEntry = symbolTableManager.findSymbol(funcName.getName());
            if (funcEntry == null) {
                error(funcName.getIdentifierToken().getLineNumber() + " " + "c");
            }
            Terminal lParen = new Terminal(advance());
            FuncRParams params = null;
            if (isStartOfExp()) {
                params = parseFuncRParams();
            }
            if (params == null) {
                if (funcEntry != null && !funcEntry.getParamTypes().isEmpty()) {
                    error(funcName.getIdentifierToken().getLineNumber() + " " + "d");
                }
            } else {
                if (funcEntry != null && funcEntry.getParamTypes().size() != params.getNumParams()) {
                    error(funcName.getIdentifierToken().getLineNumber() + " " + "d");
                }
                else if (funcEntry != null) {
                    ArrayList<Exp> exps = params.getParamExps();
                    ArrayList<SymbolEntry.Type> types = funcEntry.getParamTypes();
                    for (int i = 0; i < exps.size(); i++) {
                        if (!isSameType(exps.get(i).inferType(symbolTableManager),types.get(i))) {
                            error(funcName.getIdentifierToken().getLineNumber() + " " + "e");
                            break;
                        }
                    }
                }
            }
            Terminal rParen = null;
            if (currentToken.getType() == TokenType.RPARENT) {
                rParen = new Terminal(advance());
            } else {
                error(tokens.get(position - 1).getLineNumber() + " " + "j");
            }
            return new UnaryExp(funcName, lParen, params, rParen);
        } else {
            return new UnaryExp(parsePrimaryExp());
        }
    }

    private boolean isSameType(SymbolEntry.Type param, SymbolEntry.Type expected) {
        boolean b = param.equals(SymbolEntry.Type.IntArray) || param.equals(SymbolEntry.Type.ConstIntArray);
        if (expected.equals(SymbolEntry.Type.Int)) {
            if (b || param.equals(SymbolEntry.Type.VoidFunc)) {
                return false;
            }
        }
        if (expected.equals(SymbolEntry.Type.IntArray)) {
            return b;
        }
        return true;
    }

    private FuncRParams parseFuncRParams() {
        Exp first = parseExp();
        ArrayList<Terminal> commas = new ArrayList<>();
        ArrayList<Exp> others = new ArrayList<>();
        while (currentToken.getType() == TokenType.COMMA) {
            commas.add(new Terminal(advance()));
            others.add(parseExp());
        }
        return new FuncRParams(first, commas, others);
    }

    private Exp parseExp() {
        return new Exp(parseAddExp());
    }

    private PrimaryExp parsePrimaryExp() {
        if (currentToken.getType() == TokenType.LPARENT) {
            Terminal lParen = new Terminal(advance());
            Exp exp = parseExp();
            Terminal rParen = null;
            if (currentToken.getType() == TokenType.RPARENT) {
                rParen = new Terminal(advance());
            } else {
                error(tokens.get(position - 1).getLineNumber() + " " + "j");
            }
            return new PrimaryExp(lParen, exp, rParen);
        } else if (currentToken.getType() == TokenType.INTCON) {
            return new PrimaryExp(parseNumber());
        } else {
            return new PrimaryExp(parseLVal());
        }
    }

    private LVal parseLVal() {
        Ident ident = parseIdent();
        if (symbolTableManager.findSymbol(ident.getName()) == null) {
            error(tokens.get(position - 1).getLineNumber() + " " + "c");
        }
        Terminal lBrack = null;
        Exp exp = null;
        Terminal rBrack = null;

        if (currentToken.getType() == TokenType.LBRACK) {
            lBrack = new Terminal(advance());
            exp = parseExp();
            if (currentToken.getType() == TokenType.RBRACK) {
                rBrack = new Terminal(advance());
            } else {
                error(tokens.get(position - 1).getLineNumber() + " " + "k");
            }
        }

        return new LVal(ident, lBrack, exp, rBrack);
    }

    private Number parseNumber() {
        return new Number(new Terminal(advance()));
    }

    private ConstInitVal parseConstInitVal() {
        if (currentToken.getType() == TokenType.LBRACE) {
            Terminal lBrace = new Terminal(advance());
            ArrayList<ConstExp> constExps = new ArrayList<>();
            ArrayList<Terminal> commas = new ArrayList<>();
            if (currentToken.getType() != TokenType.RBRACE) {
                constExps.add(parseConstExp());
                while (currentToken.getType() == TokenType.COMMA) {
                    commas.add(new Terminal(advance()));
                    constExps.add(parseConstExp());
                }
            }
            Terminal rBrace = null;
            if (currentToken.getType() == TokenType.RBRACE) {
                rBrace = new Terminal(advance());
            }
            return new ConstInitVal(true, lBrace, constExps, commas, rBrace);
        } else {
            return new ConstInitVal(false, parseConstExp());
        }
    }

    private VarDecl parseVarDecl() {
        Terminal staticTk = null;
        if (currentToken.getType() == TokenType.STATICTK) {
            staticTk = new Terminal(advance());
        }
        boolean isStatic = (staticTk != null);
        BType bType = parseBType();
        VarDef firstDef = parseVarDef(isStatic);
        ArrayList<Terminal> commas = new ArrayList<>();
        ArrayList<VarDef> otherDefs = new ArrayList<>();
        while (currentToken.getType() == TokenType.COMMA) {
            commas.add(new Terminal(advance()));
            otherDefs.add(parseVarDef(isStatic));
        }
        Terminal semicn = null;
        if (currentToken.getType() == TokenType.SEMICN) {
            semicn = new Terminal(advance());
        } else {
            error(tokens.get(position - 1).getLineNumber() + " " + "i");
        }
        return new VarDecl(staticTk, bType, firstDef, commas, otherDefs, semicn);
    }

    private void addSymbolFromDeclaration(boolean isConst, boolean isStatic, Token identToken) {
        String name = identToken.getValue();
        boolean isArray = (currentToken.getType() == TokenType.LBRACK);
        SymbolEntry.Type finalType;
        if (isConst) {
            finalType = isArray ? SymbolEntry.Type.ConstIntArray : SymbolEntry.Type.ConstInt;
        } else if (isStatic) {
            finalType = isArray ? SymbolEntry.Type.StaticIntArray : SymbolEntry.Type.StaticInt;
        } else {
            finalType = isArray ? SymbolEntry.Type.IntArray : SymbolEntry.Type.Int;
        }
        int currentScopeId = symbolTableManager.getCurrentScope().getId();
        SymbolEntry entry = new SymbolEntry(name, finalType, currentScopeId);
        if (!symbolTableManager.addSymbol(entry)) {
            error(identToken.getLineNumber() + " b");
        }
    }

    private VarDef parseVarDef(boolean isStatic) {
        Ident ident = parseIdent();
        addSymbolFromDeclaration(false,isStatic,ident.getIdentifierToken());
        Terminal lBrack = null;
        ConstExp constExp = null;
        Terminal rBrack = null;

        if (currentToken.getType() == TokenType.LBRACK) {
            lBrack = new Terminal(advance());
            constExp = parseConstExp();
            if (currentToken.getType() == TokenType.RBRACK) {
                rBrack = new Terminal(advance());
            } else {
                error(tokens.get(position - 1).getLineNumber() + " " + "k");
            }
        }

        if (currentToken.getType() == TokenType.ASSIGN) {
            Terminal assign = new Terminal(advance());
            InitVal initVal = parseInitVal();
            return new VarDef(ident, lBrack, constExp, rBrack, assign, initVal);
        } else {
            return new VarDef(ident, lBrack, constExp, rBrack);
        }
    }

    private InitVal parseInitVal() {
        if (currentToken.getType() == TokenType.LBRACE) {
            Terminal lBrace = new Terminal(advance());
            ArrayList<Exp> exps = new ArrayList<>();
            ArrayList<Terminal> commas = new ArrayList<>();
            if (currentToken.getType() != TokenType.RBRACE) {
                exps.add(parseExp());
                while (currentToken.getType() == TokenType.COMMA) {
                    commas.add(new Terminal(advance()));
                    exps.add(parseExp());
                }
            }
            Terminal rBrace = null;
            if (currentToken.getType() == TokenType.RBRACE) {
                rBrace = new Terminal(advance());
            }
            return new InitVal(true, lBrace, exps, commas, rBrace);
        } else {
            return new InitVal(false, parseExp());
        }
    }

    private SymbolEntry addFunctionSymbolToParentScope(FuncType funcTypeNode, Token identToken, FuncFParams paramsNode) {
        String name = identToken.getValue();
        SymbolEntry.Type returnType = funcTypeNode.isVoid() ? SymbolEntry.Type.VoidFunc : SymbolEntry.Type.IntFunc;
        ArrayList<SymbolEntry.Type> paramTypes = new ArrayList<>();
        if (paramsNode != null) {
            for (FuncFParam param : paramsNode.getAllParams()) {
                paramTypes.add(param.getType());
            }
        }
        Scope parentScope = symbolTableManager.getParentScope();
        int parentScopeId = (parentScope != null) ? parentScope.getId() : -1;
        SymbolEntry entry = new SymbolEntry(name, returnType, paramTypes, parentScopeId);
        if (!symbolTableManager.addSymbolToParentScope(entry)) {
            error(identToken.getLineNumber() + " b");
        }
        return entry;
    }

    private FuncDef parseFuncDef() {
        FuncType funcType = parseFuncType();
        Token identToken = currentToken;
        Ident ident = parseIdent();
        Terminal lParen = new Terminal(advance());

        symbolTableManager.enterScope();

        FuncFParams params = null;
        if (currentToken.getType() != TokenType.RPARENT && currentToken.getType() != TokenType.LBRACE) {
            params = parseFuncFParams();
        }

        Terminal rParen = null;
        if (currentToken.getType() == TokenType.RPARENT) {
            rParen = new Terminal(advance());
        } else {
            error(tokens.get(position - 1).getLineNumber() + " " + "j");
        }

        addFunctionSymbolToParentScope(funcType, identToken, params);

        Block block = parseBlock();
        if (funcType.isVoid()) {
            ArrayList<Integer> lines = block.getReturnLine();
            if (lines != null && !lines.isEmpty()) {
                for (Integer line : lines) {
                    error(line + " " + "f");
                }
            }
        }
        else {
            int line = block.hasReturn();
            if (line != -1) {
                error(line + " " + "g");
            }
        }
        symbolTableManager.exitScope();
        return new FuncDef(funcType, ident, lParen, params, rParen, block);
    }

    private FuncType parseFuncType() {
        return new FuncType(new Terminal(advance()));
    }

    private FuncFParams parseFuncFParams() {
        FuncFParam first = parseFuncFParam();
        ArrayList<Terminal> commas = new ArrayList<>();
        ArrayList<FuncFParam> others = new ArrayList<>();
        while (currentToken.getType() == TokenType.COMMA) {
            commas.add(new Terminal(advance()));
            others.add(parseFuncFParam());
        }
        return new FuncFParams(first, others, commas);
    }

    private FuncFParam parseFuncFParam() {
        BType bType = parseBType();
        Ident ident = parseIdent();
        addSymbolFromDeclaration(false,false,ident.getIdentifierToken());
        Terminal lBrack = null;
        Terminal rBrack = null;

        if (currentToken.getType() == TokenType.LBRACK) {
            lBrack = new Terminal(advance());

            if (currentToken.getType() == TokenType.RBRACK) {
                rBrack = new Terminal(advance());
            } else {
                error(tokens.get(position - 1).getLineNumber() + " " + "k");
            }
        }

        return new FuncFParam(bType, ident, lBrack, rBrack);
    }

    private Block parseBlock() {
        Terminal lBrace = new Terminal(advance());
        ArrayList<BlockItem> blockItems = new ArrayList<>();
        while (currentToken.getType() != TokenType.RBRACE) {
            blockItems.add(parseBlockItem());
        }
        Terminal rBrace = new Terminal(advance());
        return new Block(lBrace, blockItems, rBrace);
    }

    private BlockItem parseBlockItem() {
        if (isDecl()) {
            return parseDecl();
        } else {
            return parseStmt();
        }
    }

    private Stmt parseStmt() {
        Terminal semicn;
        switch (currentToken.getType()) {
            case PRINTFTK: {
                Terminal printfToken = new Terminal(advance());
                Terminal lParen = new Terminal(advance());
                Terminal stringConst = new Terminal(advance());

                ArrayList<Terminal> commas = new ArrayList<>();
                ArrayList<Exp> printfExps = new ArrayList<>();
                while (currentToken.getType() == TokenType.COMMA) {
                    commas.add(new Terminal(advance()));
                    printfExps.add(parseExp());
                }

                int numExpressions = printfExps.size();

                String formatString = stringConst.getToken().getValue();
                int numFormatSpecifiers = countFormatSpecifiers(formatString);

                if (numExpressions != numFormatSpecifiers) {
                    error(printfToken.getToken().getLineNumber() + " l");
                }

                Terminal rParen = null;
                if (currentToken.getType() == TokenType.RPARENT) {
                    rParen = new Terminal(advance());
                } else {
                    error(tokens.get(position - 1).getLineNumber() + " " + "j");
                }

                semicn = null;
                if (currentToken.getType() == TokenType.SEMICN) {
                    semicn = new Terminal(advance());
                } else {
                    error(tokens.get(position - 1).getLineNumber() + " " + "i");
                }

                return new Stmt(StmtType.PrintfStmt, printfToken, lParen, stringConst, commas, printfExps, rParen, semicn);
            }

            case LBRACE:
                symbolTableManager.enterScope();
                Block block = parseBlock();
                symbolTableManager.exitScope();
                return new Stmt(StmtType.BlockStmt, block);

            case IFTK:
                Terminal ifTk = new Terminal(advance());
                Terminal lParen = new Terminal(advance());
                Cond cond = parseCond();
                Terminal rParen = null;
                if (currentToken.getType() == TokenType.RPARENT) rParen = new Terminal(advance()); else error(tokens.get(position - 1).getLineNumber() + " " + "j");
                Stmt thenStmt = parseStmt();
                Terminal elseTk = null;
                Stmt elseStmt = null;
                if (currentToken.getType() == TokenType.ELSETK) {
                    elseTk = new Terminal(advance());
                    elseStmt = parseStmt();
                }
                return new Stmt(StmtType.IfStmt, ifTk, lParen, cond, rParen, thenStmt, elseTk, elseStmt);

            case FORTK:
                Terminal forTk = new Terminal(advance());
                lParen = new Terminal(advance());
                ForStmt forStmt1 = null;
                if (currentToken.getType() != TokenType.SEMICN) forStmt1 = parseForStmt();
                Terminal semicn1 = null;
                if (currentToken.getType() == TokenType.SEMICN) semicn1 = new Terminal(advance()); else error(tokens.get(position - 1).getLineNumber() + " " + "i");
                Cond forCond = null;
                if (currentToken.getType() != TokenType.SEMICN) forCond = parseCond();
                Terminal semicn2 = null;
                if (currentToken.getType() == TokenType.SEMICN) semicn2 = new Terminal(advance()); else error(tokens.get(position - 1).getLineNumber() + " " + "i");
                ForStmt forStmt2 = null;
                if (currentToken.getType() != TokenType.RPARENT) forStmt2 = parseForStmt();
                rParen = null;
                if (currentToken.getType() == TokenType.RPARENT) rParen = new Terminal(advance()); else error(tokens.get(position - 1).getLineNumber() + " " + "j");
                forBlockNum = forBlockNum + 1;
                Stmt loopBody = parseStmt();
                forBlockNum = forBlockNum - 1;
                return new Stmt(StmtType.ForStmt, forTk, lParen, forStmt1, semicn1, forCond, semicn2, forStmt2, rParen, loopBody);

            case BREAKTK:
            case CONTINUETK:
                StmtType type = currentToken.getType() == TokenType.BREAKTK ? StmtType.BreakStmt : StmtType.ContinueStmt;
                Terminal keyword = new Terminal(advance());
                if (forBlockNum == 0) {
                    error(tokens.get(position - 1).getLineNumber() + " " + "m");
                }
                semicn = null;
                if (currentToken.getType() == TokenType.SEMICN) semicn = new Terminal(advance()); else error(tokens.get(position - 1).getLineNumber() + " " + "i");
                return new Stmt(type, keyword, semicn);

            case RETURNTK:
                Terminal returnTk = new Terminal(advance());
                Exp returnExp = null;
                if (currentToken.getType() != TokenType.SEMICN) returnExp = parseExp();
                semicn = null;
                if (currentToken.getType() == TokenType.SEMICN) semicn = new Terminal(advance()); else error(tokens.get(position - 1).getLineNumber() + " " + "i");
                return new Stmt(StmtType.ReturnStmt, returnTk, returnExp, semicn);

            default:
                if (currentToken.getType() == TokenType.SEMICN) {
                    return new Stmt(StmtType.ExpStmt, (Exp) null, new Terminal(advance()));
                }
                boolean isAssignment = false;
                int tempPos = position;
                while (tempPos < tokens.size() && tokens.get(tempPos).getType() != TokenType.SEMICN) {
                    if (tokens.get(tempPos).getType() == TokenType.ASSIGN) {
                        isAssignment = true;
                        break;
                    }
                    tempPos++;
                }
                if (isAssignment) {
                    LVal lVal = parseLVal();
                    Ident ident = lVal.getIdent();
                    SymbolEntry symbolEntry = symbolTableManager.findSymbol(ident.getName());
                    if (symbolEntry != null && symbolEntry.isConst()) {
                        error(ident.getIdentifierToken().getLineNumber() + " " + "h");
                    }
                    Terminal assign = new Terminal(advance());
                    Exp assignExp = parseExp();
                    semicn = null;
                    if (currentToken.getType() == TokenType.SEMICN) semicn = new Terminal(advance()); else error(tokens.get(position - 1).getLineNumber() + " " + "i");
                    return new Stmt(StmtType.AssignStmt, lVal, assign, assignExp, semicn);
                } else {
                    Exp defaultExp = parseExp();
                    semicn = null;
                    if (currentToken.getType() == TokenType.SEMICN) semicn = new Terminal(advance()); else error(tokens.get(position - 1).getLineNumber() + " " + "i");
                    return new Stmt(StmtType.ExpStmt, defaultExp, semicn);
                }
        }
    }

    private int countFormatSpecifiers(String str) {
        int count = 0;
        int lastIndex = 0;
        while (lastIndex != -1) {
            lastIndex = str.indexOf("%d", lastIndex);
            if (lastIndex != -1) {
                count++;
                lastIndex += 2;
            }
        }
        return count;
    }

    private Cond parseCond() {
        return new Cond(parseLOrExp());
    }

    private LOrExp parseLOrExp() {
        LAndExp lAndExp = parseLAndExp();
        LOrExp left = new LOrExp(lAndExp);
        while (currentToken.getType() == TokenType.OR) {
            Terminal op = new Terminal(advance());
            LAndExp right = parseLAndExp();
            left = new LOrExp(left, op, right);
        }
        return left;
    }

    private LAndExp parseLAndExp() {
        EqExp eqExp = parseEqExp();
        LAndExp left = new LAndExp(eqExp);
        while (currentToken.getType() == TokenType.AND) {
            Terminal op = new Terminal(advance());
            EqExp right = parseEqExp();
            left = new LAndExp(left, op, right);
        }
        return left;
    }

    private EqExp parseEqExp() {
        RelExp relExp = parseRelExp();
        EqExp left = new EqExp(relExp);
        while (currentToken.getType() == TokenType.EQL || currentToken.getType() == TokenType.NEQ) {
            Terminal op = new Terminal(advance());
            RelExp right = parseRelExp();
            left = new EqExp(left, op, right);
        }
        return left;
    }

    private RelExp parseRelExp() {
        AddExp addExp = parseAddExp();
        RelExp left = new RelExp(addExp);
        while (currentToken.getType() == TokenType.LSS || currentToken.getType() == TokenType.LEQ || currentToken.getType() == TokenType.GRE || currentToken.getType() == TokenType.GEQ) {
            Terminal op = new Terminal(advance());
            AddExp right = parseAddExp();
            left = new RelExp(left, op, right);
        }
        return left;
    }

    private ForStmt parseForStmt() {
        LVal firstLVal = parseLVal();
        Ident ident = firstLVal.getIdent();
        SymbolEntry symbolEntry = symbolTableManager.findSymbol(ident.getName());
        if (symbolEntry.isConst()) {
            error(ident.getIdentifierToken().getLineNumber() + " " + "h");
        }
        Terminal firstAssign = new Terminal(advance());
        Exp firstExp = parseExp();
        ArrayList<Terminal> commas = new ArrayList<>();
        ArrayList<LVal> otherLVals = new ArrayList<>();
        ArrayList<Terminal> otherAssigns = new ArrayList<>();
        ArrayList<Exp> otherExps = new ArrayList<>();

        while (currentToken.getType() == TokenType.COMMA) {
            commas.add(new Terminal(advance()));
            otherLVals.add(parseLVal());
            ident = otherLVals.get(otherLVals.size() - 1).getIdent();
            symbolEntry = symbolTableManager.findSymbol(ident.getName());
            if (symbolEntry.isConst()) {
                error(ident.getIdentifierToken().getLineNumber() + " " + "h");
            }
            otherAssigns.add(new Terminal(advance()));
            otherExps.add(parseExp());
        }
        return new ForStmt(firstLVal, firstAssign, firstExp, commas, otherLVals, otherAssigns, otherExps);
    }

    private MainFuncDef parseMainFuncDef() {
        Terminal intTk = new Terminal(advance());
        Terminal mainTk = new Terminal(advance());
        Terminal lParen = new Terminal(advance());
        Terminal rParen = null;
        if (currentToken.getType() == TokenType.RPARENT) {
            rParen = new Terminal(advance());
        } else {
            error(tokens.get(position - 1).getLineNumber() + " " + "j");
        }
        symbolTableManager.enterScope();
        Block block = parseBlock();
        symbolTableManager.exitScope();
        int line = block.hasReturn();
        if (line != -1) {
            error(line + " " + "g");
        }
        return new MainFuncDef(intTk, mainTk, lParen, rParen, block);
    }

    public SymbolTableManager getSymbolTableManager() {
        return symbolTableManager;
    }
}