package ast;

import java.util.ArrayList;

public class Stmt implements BlockItem {
    private final StmtType type;

    // --- Fields for different statement types ---

    // Fields shared across multiple statement types
    private Terminal lParen;
    private Terminal rParen;
    private Terminal semicn;
    private Cond cond;
    private Exp exp;

    // 1. AssignStmt: LVal = Exp;
    private LVal lVal;
    private Terminal assignToken;

    // 2. ExpStmt is covered by shared fields

    // 3. BlockStmt: Block
    private Block block;

    // 4. IfStmt: if (Cond) Stmt [else Stmt]
    private Terminal ifToken;
    private Stmt thenStmt;
    private Terminal elseToken;
    private Stmt elseStmt;

    // 5. ForStmt: for ([ForStmt]; [Cond]; [ForStmt]) Stmt
    private Terminal forToken;
    private ForStmt forStmt1;
    private Terminal semicn1;
    private Terminal semicn2;
    private ForStmt forStmt2;
    private Stmt loopBodyStmt;

    // 6. BreakStmt: break;
    private Terminal breakToken;

    // 7. ContinueStmt: continue;
    private Terminal continueToken;

    // 8. ReturnStmt: return [Exp];
    private Terminal returnToken;

    // 9. PrintfStmt: 'printf' '(' StringConst {',' Exp} ')' ';'
    private Terminal printfToken;
    private Terminal stringConst;
    private ArrayList<Terminal> commas;
    private ArrayList<Exp> printfExps;


    // --- Constructors for each statement type ---

    // Constructor for AssignStmt: LVal = Exp;
    public Stmt(StmtType type, LVal lVal, Terminal assignToken, Exp exp, Terminal semicn) {
        assert type == StmtType.AssignStmt;
        this.type = type;
        this.lVal = lVal;
        this.assignToken = assignToken;
        this.exp = exp;
        this.semicn = semicn;
    }

    // Constructor for ExpStmt: [Exp];
    public Stmt(StmtType type, Exp exp, Terminal semicn) {
        assert type == StmtType.ExpStmt;
        this.type = type;
        this.exp = exp;
        this.semicn = semicn;
    }

    // Constructor for BlockStmt: Block
    public Stmt(StmtType type, Block block) {
        assert type == StmtType.BlockStmt;
        this.type = type;
        this.block = block;
    }

    // Constructor for IfStmt: if ( Cond ) Stmt [ else Stmt ]
    public Stmt(StmtType type, Terminal ifToken, Terminal lParen, Cond cond, Terminal rParen, Stmt thenStmt, Terminal elseToken, Stmt elseStmt) {
        assert type == StmtType.IfStmt;
        this.type = type;
        this.ifToken = ifToken;
        this.lParen = lParen;
        this.cond = cond;
        this.rParen = rParen;
        this.thenStmt = thenStmt;
        this.elseToken = elseToken;
        this.elseStmt = elseStmt;
    }

    // Constructor for ForStmt: for ( [ForStmt]; [Cond]; [ForStmt] ) Stmt
    public Stmt(StmtType type, Terminal forToken, Terminal lParen, ForStmt forStmt1, Terminal semicn1, Cond cond, Terminal semicn2, ForStmt forStmt2, Terminal rParen, Stmt loopBodyStmt) {
        assert type == StmtType.ForStmt;
        this.type = type;
        this.forToken = forToken;
        this.lParen = lParen;
        this.forStmt1 = forStmt1;
        this.semicn1 = semicn1;
        this.cond = cond;
        this.semicn2 = semicn2;
        this.forStmt2 = forStmt2;
        this.rParen = rParen;
        this.loopBodyStmt = loopBodyStmt;
    }

    // Constructor for BreakStmt and ContinueStmt
    public Stmt(StmtType type, Terminal keywordToken, Terminal semicn) {
        assert type == StmtType.BreakStmt || type == StmtType.ContinueStmt;
        this.type = type;
        if (type == StmtType.BreakStmt) {
            this.breakToken = keywordToken;
        } else {
            this.continueToken = keywordToken;
        }
        this.semicn = semicn;
    }

    // Constructor for ReturnStmt: return [Exp];
    public Stmt(StmtType type, Terminal returnToken, Exp exp, Terminal semicn) {
        assert type == StmtType.ReturnStmt;
        this.type = type;
        this.returnToken = returnToken;
        this.exp = exp;
        this.semicn = semicn;
    }

    // Constructor for PrintfStmt
    public Stmt(StmtType type, Terminal printfToken, Terminal lParen, Terminal stringConst,
                ArrayList<Terminal> commas, ArrayList<Exp> printfExps, Terminal rParen, Terminal semicn) {
        assert type == StmtType.PrintfStmt;
        this.type = type;
        this.printfToken = printfToken;
        this.lParen = lParen;
        this.stringConst = stringConst;
        this.commas = commas;
        this.printfExps = printfExps;
        this.rParen = rParen;
        this.semicn = semicn;
    }

    public StmtType getType() {
        return type;
    }

    public boolean hasReturnValue() {
        if (returnToken == null) {
            return false;
        }
        else return exp != null;
    }

    public ArrayList<Integer> getReturnLine() {
        ArrayList<Integer> lines = new ArrayList<>();
        if (this.type == StmtType.ReturnStmt && this.exp != null) {
            lines.add(this.returnToken.getToken().getLineNumber());
        }
        if (this.type == StmtType.BlockStmt && this.block != null) {
            lines.addAll(this.block.getReturnLine());
        }
        if (this.type == StmtType.IfStmt) {
            if (this.thenStmt != null) {
                lines.addAll(this.thenStmt.getReturnLine());
            }
            if (this.elseStmt != null) {
                lines.addAll(this.elseStmt.getReturnLine());
            }
        }
        if (this.type == StmtType.ForStmt && this.loopBodyStmt != null) {
            lines.addAll(this.loopBodyStmt.getReturnLine());
        }
        return lines;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case AssignStmt:
                sb.append(lVal.toString());
                sb.append(assignToken.toString());
                sb.append(exp.toString());
                sb.append(semicn.toString());
                break;
            case ExpStmt:
                if (exp != null) {
                    sb.append(exp.toString());
                }
                sb.append(semicn.toString());
                break;
            case BlockStmt:
                sb.append(block.toString());
                break;
            case IfStmt:
                sb.append(ifToken.toString());
                sb.append(lParen.toString());
                sb.append(cond.toString());
                sb.append(rParen.toString());
                sb.append(thenStmt.toString());
                if (elseToken != null) {
                    sb.append(elseToken.toString());
                    sb.append(elseStmt.toString());
                }
                break;
            case ForStmt:
                sb.append(forToken.toString());
                sb.append(lParen.toString());
                if (forStmt1 != null) {
                    sb.append(forStmt1.toString());
                }
                sb.append(semicn1.toString());
                if (cond != null) {
                    sb.append(cond.toString());
                }
                sb.append(semicn2.toString());
                if (forStmt2 != null) {
                    sb.append(forStmt2.toString());
                }
                sb.append(rParen.toString());
                sb.append(loopBodyStmt.toString());
                break;
            case BreakStmt:
                sb.append(breakToken.toString());
                sb.append(semicn.toString());
                break;
            case ContinueStmt:
                sb.append(continueToken.toString());
                sb.append(semicn.toString());
                break;
            case ReturnStmt:
                sb.append(returnToken.toString());
                if (exp != null) {
                    sb.append(exp.toString());
                }
                sb.append(semicn.toString());
                break;
            case PrintfStmt:
                sb.append(printfToken.toString());
                sb.append(lParen.toString());
                sb.append(stringConst.toString());
                for (int i = 0; i < commas.size(); i++) {
                    sb.append(commas.get(i).toString());
                    sb.append(printfExps.get(i).toString());
                }
                sb.append(rParen.toString());
                sb.append(semicn.toString());
                break;
        }
        sb.append("<Stmt>\n");
        return sb.toString();
    }

    public Exp getExp() {
        return exp;
    }

    public LVal getLVal() {
        return lVal;
    }

    // Getter for block when this Stmt is a BlockStmt
    public Block getBlock() {
        return block;
    }

    // Getter for if-statement condition
    public Cond getCond() {
        return cond;
    }

    // Getter for then-statement
    public Stmt getThenStmt() {
        return thenStmt;
    }

    // Getter for else-statement
    public Stmt getElseStmt() {
        return elseStmt;
    }

    // Getter for printf string constant terminal
    public Terminal getStringConst() {
        return stringConst;
    }

    // Getter for printf argument expressions
    public ArrayList<Exp> getPrintfExps() {
        return printfExps;
    }

    // Getter for the first part of the for statement
    public ForStmt getForStmt1() {
        return forStmt1;
    }

    // Getter for the second (update) part of the for statement
    public ForStmt getForStmt2() {
        return forStmt2;
    }

    // Getter for the loop body statement
    public Stmt getLoopBodyStmt() {
        return loopBodyStmt;
    }
}