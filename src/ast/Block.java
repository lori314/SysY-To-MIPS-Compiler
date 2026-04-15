package ast;

import java.util.ArrayList;

public class Block implements Node {
    private Terminal lBrace;
    private ArrayList<BlockItem> blockItems;
    private Terminal rBrace;

    public Block(Terminal lBrace, ArrayList<BlockItem> blockItems, Terminal rBrace) {
        this.lBrace = lBrace;
        this.blockItems = blockItems;
        this.rBrace = rBrace;
    }

    public ArrayList<Integer> getReturnLine() {
        ArrayList<Integer> returnLines = new ArrayList<>();
        for (BlockItem item : blockItems) {
            if (item instanceof Stmt) {
                Stmt stmt = (Stmt) item;
                returnLines.addAll(stmt.getReturnLine());
            }
        }
        return returnLines;
    }

    public int hasReturn() {
        if (blockItems.isEmpty()) {
            return rBrace.getToken().getLineNumber();
        }

        BlockItem lastItem = blockItems.get(blockItems.size() - 1);

        if (!(lastItem instanceof Stmt)) {
            return rBrace.getToken().getLineNumber(); // 正确地报告错误
        }

        Stmt lastStmt = (Stmt) lastItem;
        if (lastStmt.hasReturnValue()) {
            return -1; // 正确
        } else {
            return rBrace.getToken().getLineNumber(); // 错误
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(lBrace);
        for (BlockItem blockItem : blockItems) {
            sb.append(blockItem.toString());
        }
        sb.append(rBrace);
        sb.append("<Block>\n");
        return sb.toString();
    }

    public ArrayList<BlockItem> getBlockItems() {
        return blockItems;
    }
}
