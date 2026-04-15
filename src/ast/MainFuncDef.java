package ast;

public class MainFuncDef implements Node {
    // MainFuncDef -> 'int' 'main' '(' ')' Block
    private final Terminal intToken;
    private final Terminal mainToken;
    private final Terminal lParen;
    private final Terminal rParen;
    private final Block block;

    public MainFuncDef(Terminal intToken, Terminal mainToken, Terminal lParen, Terminal rParen, Block block) {
        this.intToken = intToken;
        this.mainToken = mainToken;
        this.lParen = lParen;
        this.rParen = rParen;
        this.block = block;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(intToken.toString());
        sb.append(mainToken.toString());
        sb.append(lParen.toString());
        sb.append(rParen.toString());
        sb.append(block.toString());
        sb.append("<MainFuncDef>\n");
        return sb.toString();
    }

    public Block getBlock() {
        return block;
    }
}