package backend.alloc;

import java.util.*;

/**
 * 图着色寄存器分配器 (Graph Coloring Register Allocator)
 * 
 * 实现 Chaitin-Briggs 算法的简化版本：
 * 1. 构建冲突图（同时活跃的变量不能分配同一个寄存器）
 * 2. 简化：移除低度数节点
 * 3. 着色：从栈中弹出节点并分配颜色
 * 4. 溢出：如果无法着色，选择溢出代价最低的变量
 * 
 * 寄存器分配策略：
 * - $s0-$s7: Callee-saved，优先用于跨调用的变量
 * - $t0-$t6: Caller-saved，用于不跨调用的临时变量
 * - $t7-$t9: 保留给代码生成器作为临时寄存器
 */
public class GraphColoringAllocator {
    
    // 可分配的寄存器
    private static final String[] CALLEE_SAVED = {"$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7"};
    private static final String[] CALLER_SAVED = {"$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6"};
    
    // 寄存器总数 K
    private final int K = CALLEE_SAVED.length + CALLER_SAVED.length;
    
    // 冲突图：邻接表
    private Map<String, Set<String>> adjList;
    // 冲突图：边集合（用于快速查询）
    private Set<String> adjSet;
    // 节点度数
    private Map<String, Integer> degree;
    
    // 工作列表
    private Set<String> simplifyWorklist;  // 低度数非 move 相关节点
    private Set<String> spillWorklist;     // 高度数节点
    private Stack<String> selectStack;     // 简化时移除的节点
    private Set<String> spilledNodes;      // 实际溢出的节点
    private Set<String> coloredNodes;      // 已着色节点
    
    // 变量信息
    private Map<String, VarInfo> varInfoMap;
    
    // 分配结果
    private Map<String, String> colorMap;  // 变量 -> 寄存器
    private Set<String> usedCalleeSaved;   // 使用的 callee-saved 寄存器
    
    /**
     * 变量信息
     */
    public static class VarInfo {
        String name;
        int firstDef;       // 第一次定义的行号
        int lastUse;        // 最后一次使用的行号
        int useCount;       // 使用次数
        boolean crossesCall;// 是否跨越函数调用
        boolean isAlloca;   // 是否是 alloca 结果
        int maxLoopDepth;   // 变量使用时的最大循环深度
        long weightedUseCount; // 加权使用次数 (考虑循环深度)
        
        VarInfo(String name) {
            this.name = name;
            this.firstDef = Integer.MAX_VALUE;
            this.lastUse = -1;
            this.useCount = 0;
            this.crossesCall = false;
            this.isAlloca = false;
            this.maxLoopDepth = 0;
            this.weightedUseCount = 0;
        }
        
        /**
         * 添加一次使用，根据循环深度加权
         * SpillCost = Σ (InstructionWeight * (10 ^ LoopDepth))
         */
        void addUse(int loopDepth) {
            useCount++;
            if (loopDepth > maxLoopDepth) {
                maxLoopDepth = loopDepth;
            }
            // 每次使用的权重 = 10^loopDepth
            // 为避免溢出，限制 loopDepth 最大为 6
            int effectiveDepth = Math.min(loopDepth, 6);
            long weight = 1;
            for (int i = 0; i < effectiveDepth; i++) {
                weight *= 10;
            }
            weightedUseCount += weight;
        }
        
        long getSpillCost() {
            // 溢出代价：加权使用次数
            // 循环内的变量权重远高于循环外的
            long cost = weightedUseCount;
            // 跨调用变量更重要，额外加权
            if (crossesCall) {
                cost += 1000L * (maxLoopDepth + 1);
            }
            return cost;
        }
    }
    
    /**
     * 对一个函数进行寄存器分配
     * 
     * @param functionLines 函数体的 LLVM IR 行
     * @return 分配结果，变量名 -> 寄存器名
     */
    public AllocationResult allocate(List<String> functionLines) {
        init();
        
        // 1. 收集变量信息
        collectVarInfo(functionLines);
        
        // 2. 构建冲突图
        buildInterferenceGraph();
        
        // 3. 初始化工作列表
        makeWorklist();
        
        // 4. 迭代简化和着色
        while (!simplifyWorklist.isEmpty() || !spillWorklist.isEmpty()) {
            if (!simplifyWorklist.isEmpty()) {
                simplify();
            } else if (!spillWorklist.isEmpty()) {
                selectSpill();
            }
        }
        
        // 5. 着色
        assignColors();
        
        return new AllocationResult(colorMap, usedCalleeSaved, spilledNodes);
    }
    
    /**
     * 初始化数据结构
     */
    private void init() {
        adjList = new HashMap<>();
        adjSet = new HashSet<>();
        degree = new HashMap<>();
        simplifyWorklist = new HashSet<>();
        spillWorklist = new HashSet<>();
        selectStack = new Stack<>();
        spilledNodes = new HashSet<>();
        coloredNodes = new HashSet<>();
        varInfoMap = new HashMap<>();
        colorMap = new HashMap<>();
        usedCalleeSaved = new HashSet<>();
    }
    
    /**
     * 收集变量信息
     */
    private void collectVarInfo(List<String> lines) {
        List<Integer> callSites = new ArrayList<>();
        
        // 预处理：计算每行的循环深度（改进版）
        int[] loopDepth = new int[lines.size()];
        
        // 第一步：找出所有循环标签的行号范围
        // 常见模式：
        // - loop_cond_N / loop_body_N / loop_end_N
        // - while_cond_N / while_body_N / while_end_N
        // - for_cond_N / for_body_N / for_end_N
        Map<String, Integer> labelToLine = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.endsWith(":")) {
                String label = line.substring(0, line.length() - 1);
                labelToLine.put(label, i);
            }
        }
        
        // 第二步：找出所有循环区间 [condLine, endLine)
        List<int[]> loopRanges = new ArrayList<>();
        for (String label : labelToLine.keySet()) {
            String lowerLabel = label.toLowerCase();
            // 匹配循环条件标签
            if (lowerLabel.contains("loop_cond") || lowerLabel.contains("while_cond") || 
                lowerLabel.contains("for_cond") || lowerLabel.contains("for.cond") ||
                lowerLabel.matches(".*loop\\d*$")) {
                
                int condLine = labelToLine.get(label);
                
                // 尝试找到对应的 end 标签
                String baseName = label.replaceAll("_cond", "").replaceAll("loop(\\d+)$", "loop_end$1");
                String endLabel1 = label.replace("_cond", "_end").replace(".cond", ".end");
                String endLabel2 = label.replace("loop_cond", "loop_next").replace("while_cond", "while_next");
                String endLabel3 = label.replace("_cond_", "_next_").replace(".cond.", ".next.");
                
                int endLine = lines.size(); // 默认到函数结束
                for (String end : Arrays.asList(endLabel1, endLabel2, endLabel3)) {
                    if (labelToLine.containsKey(end)) {
                        endLine = Math.min(endLine, labelToLine.get(end));
                    }
                }
                
                // 也检查 _end 后缀（无条件）
                for (String possibleEnd : labelToLine.keySet()) {
                    String lowerEnd = possibleEnd.toLowerCase();
                    if ((lowerEnd.contains("loop_next") || lowerEnd.contains("loop_end") ||
                         lowerEnd.contains("while_end") || lowerEnd.contains("for_end") ||
                         lowerEnd.contains("for.end")) && 
                        labelToLine.get(possibleEnd) > condLine) {
                        endLine = Math.min(endLine, labelToLine.get(possibleEnd));
                    }
                }
                
                if (endLine > condLine) {
                    loopRanges.add(new int[]{condLine, endLine});
                }
            }
        }
        
        // 第三步：计算每行的循环深度
        for (int i = 0; i < lines.size(); i++) {
            int depth = 0;
            for (int[] range : loopRanges) {
                if (i >= range[0] && i < range[1]) {
                    depth++;
                }
            }
            loopDepth[i] = depth;
        }
        
        // 第一遍：找出所有调用点
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("call ") && !line.contains("@printf") && !line.contains("@getint")) {
                callSites.add(i);
            }
        }
        
        // 第二遍：收集变量信息，使用循环深度加权
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = line.replaceAll(",", " ").split("\\s+");
            int depth = loopDepth[i];
            
            // 检测定义
            if (line.contains("=") && !line.trim().startsWith(";")) {
                String def = parts[0];
                if (def.startsWith("%")) {
                    VarInfo info = varInfoMap.computeIfAbsent(def, VarInfo::new);
                    if (info.firstDef > i) info.firstDef = i;
                    if (line.contains("alloca")) info.isAlloca = true;
                }
            }
            
            // 检测使用，使用循环深度加权
            for (String p : parts) {
                if (p.startsWith("%")) {
                    VarInfo info = varInfoMap.computeIfAbsent(p, VarInfo::new);
                    if (info.lastUse < i) info.lastUse = i;
                    info.addUse(depth);
                }
            }
        }
        
        // 计算是否跨调用
        for (VarInfo info : varInfoMap.values()) {
            if (info.lastUse < 0) continue;
            for (int callSite : callSites) {
                if (callSite > info.firstDef && callSite < info.lastUse) {
                    info.crossesCall = true;
                    break;
                }
            }
        }
    }
    
    /**
     * 构建冲突图
     * 两个变量在同一时刻活跃则冲突
     */
    private void buildInterferenceGraph() {
        List<VarInfo> vars = new ArrayList<>();
        for (VarInfo info : varInfoMap.values()) {
            // 跳过 alloca 结果和无效变量
            if (info.isAlloca || info.lastUse < 0) continue;
            vars.add(info);
            degree.put(info.name, 0);
        }
        
        // 简化的活跃分析：如果两个变量的活跃区间重叠，则冲突
        for (int i = 0; i < vars.size(); i++) {
            VarInfo v1 = vars.get(i);
            for (int j = i + 1; j < vars.size(); j++) {
                VarInfo v2 = vars.get(j);
                
                // 检查活跃区间是否重叠
                if (intervalsOverlap(v1.firstDef, v1.lastUse, v2.firstDef, v2.lastUse)) {
                    addEdge(v1.name, v2.name);
                }
            }
        }
    }
    
    /**
     * 检查两个区间是否重叠
     */
    private boolean intervalsOverlap(int s1, int e1, int s2, int e2) {
        return !(e1 < s2 || e2 < s1);
    }
    
    /**
     * 添加冲突边
     */
    private void addEdge(String u, String v) {
        String edgeKey = u.compareTo(v) < 0 ? u + "," + v : v + "," + u;
        if (!adjSet.contains(edgeKey) && !u.equals(v)) {
            adjSet.add(edgeKey);
            
            adjList.computeIfAbsent(u, k -> new HashSet<>()).add(v);
            adjList.computeIfAbsent(v, k -> new HashSet<>()).add(u);
            
            degree.compute(u, (k, val) -> val == null ? 1 : val + 1);
            degree.compute(v, (k, val) -> val == null ? 1 : val + 1);
        }
    }
    
    /**
     * 初始化工作列表
     */
    private void makeWorklist() {
        for (String var : degree.keySet()) {
            if (degree.get(var) >= K) {
                spillWorklist.add(var);
            } else {
                simplifyWorklist.add(var);
            }
        }
    }
    
    /**
     * 简化：移除低度数节点
     */
    private void simplify() {
        String n = simplifyWorklist.iterator().next();
        simplifyWorklist.remove(n);
        selectStack.push(n);
        
        // 降低邻居的度
        for (String adj : getAdjacent(n)) {
            decreaseDegree(adj);
        }
    }
    
    /**
     * 获取节点的有效邻居（排除已简化和已溢出的）
     */
    private Set<String> getAdjacent(String n) {
        Set<String> result = new HashSet<>();
        Set<String> neighbors = adjList.getOrDefault(n, Collections.emptySet());
        for (String adj : neighbors) {
            if (!selectStack.contains(adj) && !spilledNodes.contains(adj)) {
                result.add(adj);
            }
        }
        return result;
    }
    
    /**
     * 降低节点度
     */
    private void decreaseDegree(String n) {
        int d = degree.getOrDefault(n, 0);
        if (d <= 0) return;
        degree.put(n, d - 1);
        
        // 如果从 K 降到 K-1，从溢出列表移到简化列表
        if (d == K) {
            spillWorklist.remove(n);
            simplifyWorklist.add(n);
        }
    }
    
    /**
     * 选择溢出节点
     */
    private void selectSpill() {
        // 选择溢出代价最低的节点
        String minNode = null;
        long minCost = Long.MAX_VALUE;
        
        for (String n : spillWorklist) {
            VarInfo info = varInfoMap.get(n);
            if (info != null) {
                long cost = info.getSpillCost();
                // 考虑度数：度数越高，溢出越有利于简化图
                // 使用更大的度数权重来平衡循环深度权重
                cost -= degree.getOrDefault(n, 0) * 50L;
                if (cost < minCost) {
                    minCost = cost;
                    minNode = n;
                }
            }
        }
        
        if (minNode != null) {
            spillWorklist.remove(minNode);
            // 乐观着色：先尝试简化，如果着色失败再真正溢出
            simplifyWorklist.add(minNode);
        }
    }
    
    /**
     * 着色：从栈中弹出节点并分配寄存器
     */
    private void assignColors() {
        while (!selectStack.isEmpty()) {
            String n = selectStack.pop();
            
            // 收集邻居已使用的颜色
            Set<String> usedColors = new HashSet<>();
            for (String adj : adjList.getOrDefault(n, Collections.emptySet())) {
                String adjColor = colorMap.get(adj);
                if (adjColor != null) {
                    usedColors.add(adjColor);
                }
            }
            
            // 选择颜色
            String color = null;
            VarInfo info = varInfoMap.get(n);
            
            // 如果跨调用，优先用 callee-saved
            if (info != null && info.crossesCall) {
                for (String reg : CALLEE_SAVED) {
                    if (!usedColors.contains(reg)) {
                        color = reg;
                        usedCalleeSaved.add(reg);
                        break;
                    }
                }
            }
            
            // 如果没分配到，尝试所有可用寄存器
            if (color == null) {
                for (String reg : CALLER_SAVED) {
                    if (!usedColors.contains(reg)) {
                        color = reg;
                        break;
                    }
                }
            }
            if (color == null) {
                for (String reg : CALLEE_SAVED) {
                    if (!usedColors.contains(reg)) {
                        color = reg;
                        usedCalleeSaved.add(reg);
                        break;
                    }
                }
            }
            
            if (color != null) {
                colorMap.put(n, color);
                coloredNodes.add(n);
            } else {
                // 无法着色，标记为溢出
                spilledNodes.add(n);
            }
        }
    }
    
    /**
     * 分配结果
     */
    public static class AllocationResult {
        public final Map<String, String> registerMap;
        public final Set<String> usedCalleeSaved;
        public final Set<String> spilledVariables;
        
        AllocationResult(Map<String, String> registerMap, Set<String> usedCalleeSaved,
                        Set<String> spilledVariables) {
            this.registerMap = registerMap;
            this.usedCalleeSaved = usedCalleeSaved;
            this.spilledVariables = spilledVariables;
        }
    }
}
