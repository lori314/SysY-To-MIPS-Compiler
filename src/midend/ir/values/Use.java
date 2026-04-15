package midend.ir.values;

public class Use {
    public User user; // 谁在用
    public Value value; // 用了谁

    public Use(User user, Value value) {
        this.user = user;
        this.value = value;
    }
}
