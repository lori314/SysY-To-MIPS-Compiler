package midend.optim;

import midend.ir.values.BasicBlock;
import midend.ir.values.Function;
import midend.ir.values.Module;

import java.util.*;

public class DomAnalysis {
    // 记录每个块的直接支配者 (Immediate Dominator)
    private final Map<BasicBlock, BasicBlock> idom = new HashMap<>();
    // 记录每个块的支配边界 (Dominance Frontier)
    private final Map<BasicBlock, Set<BasicBlock>> domFrontier = new HashMap<>();

    public void run(Module module) {
        idom.clear();
        domFrontier.clear();
        for (Function func : module.getFunctions()) {
            if (func.getBasicBlocks().isEmpty()) continue;
            computeIDom(func);
            computeDomFrontier(func);
        }
    }

    // 计算直接支配者 (IDom)
    private void computeIDom(Function func) {
        // idom.clear(); // Removed to persist across functions
        BasicBlock entry = func.getBasicBlocks().getFirst();
        
        // 初始化：entry 的 idom 是它自己
        idom.put(entry, entry);
        
        // 获取逆后序遍历 (Reverse Post Order) 可以加速收敛，这里简单起见用普通顺序，多迭代几次
        List<BasicBlock> blocks = func.getBasicBlocks();
        
        boolean changed = true;
        while (changed) {
            changed = false;
            for (BasicBlock bb : blocks) {
                if (bb == entry) continue;
                
                // 寻找第一个已经计算出 idom 的前驱
                BasicBlock newIdom = null;
                for (BasicBlock pred : bb.getPredecessors()) {
                    if (idom.containsKey(pred)) {
                        newIdom = pred;
                        break;
                    }
                }
                
                if (newIdom == null) continue; // 暂时不可达或由死代码导致

                // 与其他已计算 idom 的前驱求公共祖先 (intersect)
                for (BasicBlock pred : bb.getPredecessors()) {
                    if (pred != newIdom && idom.containsKey(pred)) {
                        newIdom = intersect(newIdom, pred);
                    }
                }

                if (!newIdom.equals(idom.get(bb))) {
                    idom.put(bb, newIdom);
                    changed = true;
                }
            }
        }
    }

    // 查找两个节点在支配树上的最近公共祖先
    private BasicBlock intersect(BasicBlock b1, BasicBlock b2) {
        Set<BasicBlock> path1 = new HashSet<>();
        BasicBlock curr = b1;
        while (curr != idom.get(curr)) { // 直到 root
            path1.add(curr);
            curr = idom.get(curr);
        }
        path1.add(curr); // add root

        curr = b2;
        while (!path1.contains(curr)) {
            curr = idom.get(curr);
        }
        return curr;
    }

    // 计算支配边界 (Dominance Frontier)
    private void computeDomFrontier(Function func) {
        // domFrontier.clear(); // Removed
        for (BasicBlock bb : func.getBasicBlocks()) {
            domFrontier.put(bb, new HashSet<>());
        }

        for (BasicBlock bb : func.getBasicBlocks()) {
            // 如果一个块有多个前驱，它可能是某些块的支配边界
            if (bb.getPredecessors().size() >= 2) {
                for (BasicBlock pred : bb.getPredecessors()) {
                    BasicBlock runner = pred;
                    // 只要 runner 不是 bb 的直接支配者，就向上跑
                    while (runner != idom.get(bb)) {
                        domFrontier.get(runner).add(bb);
                        runner = idom.get(runner);
                        if (runner == null) break; // 防止死循环
                    }
                }
            }
        }
    }

    public Set<BasicBlock> getDomFrontier(BasicBlock bb) {
        return domFrontier.getOrDefault(bb, Collections.emptySet());
    }

    public BasicBlock getIDom(BasicBlock bb) {
        return idom.get(bb);
    }

    /**
     * 判断 a 是否支配 b
     * a 支配 b 当且仅当从入口到 b 的所有路径都经过 a
     */
    public boolean dominates(BasicBlock a, BasicBlock b) {
        if (a == b) return true;
        BasicBlock runner = b;
        int limit = 1000; // 防止无限循环
        while (runner != null && limit-- > 0) {
            BasicBlock newRunner = idom.get(runner);
            if (newRunner == runner) break; // 到达入口
            runner = newRunner;
            if (runner == a) return true;
        }
        return false;
    }
}
