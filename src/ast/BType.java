package ast;

public class BType {
    Terminal intTk;

    public BType(Terminal intTk) {
        this.intTk = intTk;
    }

    @Override
    public String toString() {
        return intTk.toString();
    }
}
