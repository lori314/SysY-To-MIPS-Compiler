package midend.ir.values;

import midend.ir.types.Type;
import java.util.ArrayList;
import java.util.Iterator;

public abstract class Value {
    protected String name;
    protected Type type;
    private final ArrayList<Use> useList = new ArrayList<>(); // 谁使用了我

    public Value(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public Type getType() { return type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    // 添加引用
    public void addUse(Use use) {
        useList.add(use);
    }

    // 移除引用 (在 User 更换操作数时调用)
    public void removeUse(User user) {
        useList.removeIf(u -> u.user == user);
    }
    
    // 获取所有使用者
    public ArrayList<User> getUsers() {
        ArrayList<User> users = new ArrayList<>();
        for(Use u : useList) users.add(u.user);
        return users;
    }

    // 核心方法：将所有使用我的地方，替换为 newValue
    // 这是 Mem2Reg 和死代码删除的核心
    public void replaceAllUsesWith(Value newValue) {
        if (this == newValue) return;
        // 遍历所有使用当前 Value 的 User
        // 必须使用副本遍历，因为 modify 会改变 useList
        ArrayList<Use> usesCopy = new ArrayList<>(useList);
        for (Use use : usesCopy) {
            User user = use.user;
            // 让 user 把操作数从 'this' 换成 'newValue'
            user.replaceUsesOfWith(this, newValue);
        }
    }
    
    @Override
    public abstract String toString();
}
