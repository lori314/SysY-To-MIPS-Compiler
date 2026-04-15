package backend.mips;

import backend.target.MulOptimizer;
import backend.target.DivOptimizer;
import backend.target.MagicNumber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class IRtoMIPSConverter {

    private final List<String> llvmLines;
    private final StringBuilder dataSection = new StringBuilder(".data\n");
    private final StringBuilder textSection = new StringBuilder(); 

    // Per-function state
    private Map<String, Integer> stackOffsetMap;
    private int frameSize;
    private String currentFunctionName;
    private Map<String, String> defLines;
    
    // Register Allocation State
    private Map<String, String> registerMap;
    private Set<String> usedSRegs;
    
    // Static array promotion counter (for main function)
    private int staticArrayCounter = 0;
    
    // Printf 内联优化：存储字符串字面量内容
    private Map<String, String> stringMap = new HashMap<>();

    public IRtoMIPSConverter(String llvmIrCode) {
        this.llvmLines = List.of(llvmIrCode.replace("\r\n", "\n").split("\n"));
    }

    /**
     * 获取操作数所在的寄存器（如果已分配），否则返回null
     * 这个方法不生成任何代码，只是查询
     */
    private String getOperandReg(String operand) {
        if (registerMap != null && registerMap.containsKey(operand)) {
            return registerMap.get(operand);
        }
        return null;
    }
    
    /**
     * 确保操作数在寄存器中，返回寄存器名
     * 如果操作数已在分配的寄存器中，直接返回该寄存器（无代码生成）
     * 否则加载到 fallbackReg 并返回 fallbackReg
     */
    private String ensureInReg(String operand, String fallbackReg) {
        // 如果操作数已经分配了寄存器，直接使用
        if (registerMap != null && registerMap.containsKey(operand)) {
            return registerMap.get(operand);
        }
        // 否则加载到 fallback 寄存器
        loadOperandToReg(operand, fallbackReg);
        return fallbackReg;
    }

    private void loadOperandToReg(String operand, String register) {
        if (registerMap != null && registerMap.containsKey(operand)) {
            String allocatedReg = registerMap.get(operand);
            // Optimize: avoid move if same register
            if (!allocatedReg.equals(register)) {
                textSection.append("  move ").append(register).append(", ").append(allocatedReg).append("\n");
            }
            return;
        }

        if (operand.startsWith("%")) {
            // Check if variable needs stack access
            if (!stackOffsetMap.containsKey(operand)) {
                // Variable not allocated - this shouldn't happen in correct code
                textSection.append("  # WARNING: Variable ").append(operand).append(" not allocated\n");
                textSection.append("  li ").append(register).append(", 0\n");
                return;
            }
            
            int offset = stackOffsetMap.get(operand);
            String def = defLines.get(operand);
            
            // Check for statically promoted arrays (moved to .data section)
            if (def != null && def.startsWith("STATIC:")) {
                String staticLabel = def.substring(7); // Remove "STATIC:" prefix
                textSection.append("  la ").append(register).append(", ").append(staticLabel).append("\n");
            }
            // 若该 SSA 寄存器由 alloca 定义，则该寄存器语义是"地址"，需要传地址
            else if (def != null && def.contains("alloca")) {
                textSection.append("  addiu ").append(register).append(", $fp, ").append(offset).append("\n");
            } else {
                textSection.append("  lw ").append(register).append(", ").append(offset).append("($fp)\n");
            }
        } else if (operand.startsWith("@")) {
            // 全局变量：传递其地址，用 la 加载标签地址
            String gName = operand.substring(1).replace('.', '_');
            textSection.append("  la ").append(register).append(", ").append(gName).append("\n");
        } else {
            // 立即数
            textSection.append("  li ").append(register).append(", ").append(operand).append("\n");
        }
    }

    public String convert() {
        for (String line : llvmLines) parseGlobal(line.trim());

        for (int i = 0; i < llvmLines.size(); i++) {
            String line = llvmLines.get(i).trim();
            if (line.startsWith("define")) {
                i = processFunction(i);
            }
        }

        StringBuilder finalMips = new StringBuilder();
        finalMips.append(dataSection.toString());
        finalMips.append("\n.text\n");
        finalMips.append(".globl main\n");
        finalMips.append("\n# --- Program Entry Point Wrapper ---\n");
        finalMips.append("_start:\n"); 
        finalMips.append("  jal main\n");        
        finalMips.append("  li $v0, 10\n");      
        finalMips.append("  syscall\n");          

        appendRuntimeLibrary();
        finalMips.append(textSection.toString());
        return finalMips.toString();
    }

    private String getTempReg(int index) { return "$t" + index; }

    private void parseGlobal(String line) {
        if (!line.startsWith("@")) return;
        int eq = line.indexOf('=');
        if (eq < 0) return;
        String name = line.substring(1, eq).trim();
        String def = line.substring(eq + 1).trim();
        String mipsLabel = name.replace('.', '_');

        if (def.contains("c\"")) {
            int cIndex = def.indexOf("c\"");
            if (cIndex >= 0) {
                int start = cIndex + 2;
                int end = def.lastIndexOf('"');
                if (end > start) {
                    String content = def.substring(start, end);
                    // 存储原始内容到 stringMap（用于 printf 内联）
                    String javaContent = content.replace("\\0A", "\n").replace("\\00", "");
                    stringMap.put(mipsLabel, javaContent);
                    
                    // 修正转义符：将 LLVM 的 \0A 转为 MIPS 的 \n（实际换行）
                    content = content.replace("\\0A", "\\n"); 
                    content = content.replace("\\00", "");
                    dataSection.append(mipsLabel).append(": .asciiz \"").append(content).append("\"\n");
                    return;
                }
            }
        }
        // 处理数组 / 普通变量 / zeroinitializer
        // 形式: @arr = global [10 x i32] [i32 1, i32 2, i32 3, ...]
        // 或 @arr = global [10 x i32] zeroinitializer
        if (def.contains("global ") || def.contains("constant ")) {
            // 判断是否为数组
            int firstBracket = def.indexOf('[');
            int firstClose = def.indexOf(']');
            if (firstBracket >= 0 && firstClose > firstBracket && def.substring(firstBracket, firstClose).contains("x i32")) {
                // 提取数组长度
                String header = def.substring(firstBracket + 1, firstClose).trim(); // e.g. 10 x i32
                String sizePart = header.split("x")[0].trim();
                int arrSize = 0;
                try { arrSize = Integer.parseInt(sizePart); } catch (Exception ignored) {}

                // 查找初始化列表
                boolean hasInitializerList = false;
                List<String> initValues = new ArrayList<>();
                int secondOpen = def.indexOf('[', firstClose + 1);
                int secondClose = def.lastIndexOf(']');
                if (secondOpen > firstClose && secondClose > secondOpen) {
                    String initContent = def.substring(secondOpen + 1, secondClose).trim();
                    if (!initContent.isEmpty()) {
                        // 判断是否为 i32 列表
                        if (initContent.contains("i32")) {
                            hasInitializerList = true;
                            String[] entries = initContent.split(",");
                            for (String e : entries) {
                                e = e.trim();
                                if (e.startsWith("i32")) {
                                    String[] parts = e.split("\\s+");
                                    if (parts.length >= 2) initValues.add(parts[1]);
                                } else if (e.matches("-?\\d+")) {
                                    initValues.add(e);
                                }
                            }
                        }
                    }
                }

                dataSection.append(".align 2\n");
                if (def.contains("zeroinitializer")) {
                    // 全零初始化
                    int bytes = arrSize * 4;
                    dataSection.append(mipsLabel).append(": .space ").append(bytes).append("\n");
                    return;
                }
                if (hasInitializerList && !initValues.isEmpty()) {
                    dataSection.append(mipsLabel).append(": .word ");
                    dataSection.append(String.join(", ", initValues)).append("\n");
                    return;
                } else {
                    // 没有初值列表，默认零填充
                    int bytes = arrSize * 4;
                    dataSection.append(mipsLabel).append(": .space ").append(bytes).append("\n");
                    return;
                }
            }
        }
        // 普通单个 i32 全局变量：@x = global i32 5
        if (def.startsWith("global i32") || def.startsWith("constant i32") || def.contains(" i32 ")) {
            // 尝试寻找最后一个数字
            String val = "0";
            String[] toks = def.split(" ");
            for (int i = toks.length - 1; i >= 0; i--) {
                if (toks[i].matches("-?\\d+")) { val = toks[i]; break; }
            }
            dataSection.append(".align 2\n");
            dataSection.append(mipsLabel).append(": .word ").append(val).append("\n");
        }
    }

    private void allocateRegisters(List<String> bodyLines) {
        registerMap = new HashMap<>();
        usedSRegs = new HashSet<>();
        
        // ========== 循环深度分析 ==========
        int[] loopDepth = computeLoopDepth(bodyLines);
        
        // ========== 活跃区间分析 (Liveness Analysis) ==========
        Map<String, Integer> firstDef = new HashMap<>();  // 第一次定义
        Map<String, Integer> lastUse = new HashMap<>();   // 最后一次使用
        Map<String, Long> weightedUseCount = new HashMap<>();  // 加权使用次数
        Map<String, Integer> maxLoopDepth = new HashMap<>();   // 最大循环深度
        Map<String, Boolean> isAlloca = new HashMap<>();  // 是否是 alloca
        
        // 收集所有调用点（包括内置函数 printf/getint，因为它们也会破坏 caller-saved 寄存器）
        List<Integer> allCallSites = new ArrayList<>();
        for (int i = 0; i < bodyLines.size(); i++) {
            String line = bodyLines.get(i);
            if (line.contains("call ")) {
                allCallSites.add(i);
            }
        }
        
        // 扫描所有指令，收集活跃信息（考虑循环深度）
        for (int i = 0; i < bodyLines.size(); i++) {
            String line = bodyLines.get(i);
            String[] parts = line.replaceAll(",", " ").split("\\s+");
            int depth = loopDepth[i];
            
            // 计算权重：10^loopDepth (限制最大深度为6)
            int effectiveDepth = Math.min(depth, 6);
            long weight = 1;
            for (int d = 0; d < effectiveDepth; d++) weight *= 10;
            
            // 检测定义
            if (line.contains("=") && !line.trim().startsWith(";")) {
                String def = parts[0];
                if (def.startsWith("%")) {
                    if (!firstDef.containsKey(def)) {
                        firstDef.put(def, i);
                    }
                    // 检查是否是 alloca
                    if (line.contains("alloca")) {
                        isAlloca.put(def, true);
                    }
                    // 定义也计入加权使用
                    weightedUseCount.merge(def, weight, Long::sum);
                    maxLoopDepth.merge(def, depth, Math::max);
                }
            }
            
            // 检测使用
            for (String p : parts) {
                // 清理变量名，去除括号等杂质
                String cleaned = p.replaceAll("[\\(\\)\\[\\]]", "");
                if (cleaned.startsWith("%")) {
                    lastUse.put(cleaned, i);
                    weightedUseCount.merge(cleaned, weight, Long::sum);
                    maxLoopDepth.merge(cleaned, depth, Math::max);
                }
            }
        }
        
        // 计算每个变量是否跨越调用
        Map<String, Boolean> crossesCall = new HashMap<>();
        for (String var : firstDef.keySet()) {
            if (!lastUse.containsKey(var)) continue;
            int start = firstDef.get(var);
            int end = lastUse.get(var);
            boolean crosses = false;
            for (int callSite : allCallSites) {
                // 调用在变量定义之后（包括同一行）且在最后使用之前（包括同一行）
                if (callSite >= start && callSite <= end) {
                    crosses = true;
                    break;
                }
            }
            crossesCall.put(var, crosses);
        }
        
        // ========== 线性扫描寄存器分配 ==========
        // 构建活跃区间列表
        List<LiveInterval> intervals = new ArrayList<>();
        for (String var : firstDef.keySet()) {
            // 跳过 alloca 结果（它们需要地址）
            if (isAlloca.getOrDefault(var, false)) continue;
            if (!lastUse.containsKey(var)) continue;
            
            LiveInterval interval = new LiveInterval();
            interval.var = var;
            interval.start = firstDef.get(var);
            interval.end = lastUse.get(var);
            interval.crossesCall = crossesCall.getOrDefault(var, false);
            interval.maxLoopDepth = maxLoopDepth.getOrDefault(var, 0);
            
            // 溢出代价 = 加权使用次数 (循环内变量代价极高)
            // 使用 1000^loopDepth 权重，确保深层循环变量优先分配寄存器
            int depth = interval.maxLoopDepth;
            int effectiveDepth = Math.min(depth, 4); // 限制最大深度
            long depthMultiplier = 1;
            for (int d = 0; d < effectiveDepth; d++) depthMultiplier *= 1000;
            interval.spillCost = weightedUseCount.getOrDefault(var, 1L) * depthMultiplier;
            if (interval.crossesCall) {
                interval.spillCost += 1000L * depthMultiplier;
            }
            
            intervals.add(interval);
        }
        
        // 按起始点排序
        intervals.sort(Comparator.comparingInt(a -> a.start));
        
        // ========== 寄存器池 ==========
        // Callee-saved: $s0-$s7 (8个) - 跨调用安全
        // Caller-saved: $t4-$t9, $v1 (7个) - 仅用于不跨调用的变量
        // 注意：$t0-$t3 保留给代码生成临时使用
        LinkedList<String> freeCalleeSaved = new LinkedList<>();
        LinkedList<String> freeCallerSaved = new LinkedList<>();
        for (int i = 0; i <= 7; i++) freeCalleeSaved.add("$s" + i);
        for (int i = 4; i <= 9; i++) freeCallerSaved.add("$t" + i);
        freeCallerSaved.add("$v1");
        
        // 活跃区间（按结束点排序）
        PriorityQueue<LiveInterval> active = new PriorityQueue<>(
            Comparator.comparingInt(a -> a.end)
        );
        
        for (LiveInterval current : intervals) {
            // 释放已结束的区间
            while (!active.isEmpty() && active.peek().end < current.start) {
                LiveInterval expired = active.poll();
                if (expired.assignedReg != null) {
                    if (expired.assignedReg.startsWith("$s")) {
                        freeCalleeSaved.addFirst(expired.assignedReg);
                    } else {
                        freeCallerSaved.addFirst(expired.assignedReg);
                    }
                }
            }
            
            // 尝试分配寄存器
            String reg = null;
            
            // 跨调用变量必须使用 callee-saved 寄存器，否则溢出
            if (current.crossesCall) {
                if (!freeCalleeSaved.isEmpty()) {
                    reg = freeCalleeSaved.removeFirst();
                    usedSRegs.add(reg);
                }
                // 如果没有 callee-saved，则不分配寄存器（溢出到栈）
            } else {
                // 不跨调用的变量优先使用 caller-saved（避免不必要的保存）
                if (!freeCallerSaved.isEmpty()) {
                    reg = freeCallerSaved.removeFirst();
                } else if (!freeCalleeSaved.isEmpty()) {
                    reg = freeCalleeSaved.removeFirst();
                    usedSRegs.add(reg);
                }
            }
            
            if (reg != null) {
                current.assignedReg = reg;
                registerMap.put(current.var, reg);
                active.add(current);
            } else {
                // 需要溢出：选择溢出代价最低的活跃区间
                LiveInterval toSpill = null;
                for (LiveInterval a : active) {
                    // 循环内变量绝不溢出（spill cost 极高）
                    if (a.maxLoopDepth > 0 && current.maxLoopDepth > 0) {
                        // 都在循环内，选择代价更低的
                        if (a.spillCost < current.spillCost) {
                            if (toSpill == null || a.spillCost < toSpill.spillCost) {
                                toSpill = a;
                            }
                        }
                    } else if (a.maxLoopDepth == 0 && current.maxLoopDepth > 0) {
                        // 当前在循环内，抢占循环外的
                        if (toSpill == null || a.spillCost < toSpill.spillCost) {
                            toSpill = a;
                        }
                    } else if (a.end > current.end && a.spillCost < current.spillCost) {
                        if (toSpill == null || a.spillCost < toSpill.spillCost) {
                            toSpill = a;
                        }
                    }
                }
                
                if (toSpill != null) {
                    // 抢占寄存器
                    String stolenReg = toSpill.assignedReg;
                    active.remove(toSpill);
                    registerMap.remove(toSpill.var);
                    toSpill.assignedReg = null;
                    
                    current.assignedReg = stolenReg;
                    registerMap.put(current.var, stolenReg);
                    active.add(current);
                }
                // 否则当前变量溢出到栈（不分配寄存器）
            }
        }
    }
    
    // 计算每行的循环深度
    private int[] computeLoopDepth(List<String> lines) {
        int[] depth = new int[lines.size()];
        
        // 找出所有标签位置
        Map<String, Integer> labelToLine = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.endsWith(":") && !line.contains(" ")) {
                labelToLine.put(line.substring(0, line.length() - 1), i);
            }
        }
        
        // 找出循环区间
        List<int[]> loopRanges = new ArrayList<>();
        for (String label : labelToLine.keySet()) {
            String lower = label.toLowerCase();
            if (lower.contains("loop_cond") || lower.contains("for_cond") || 
                lower.contains("for.cond") || lower.contains("while_cond")) {
                int condLine = labelToLine.get(label);
                
                // 找对应的 end/next 标签
                String endLabel = label.replace("_cond", "_next")
                                       .replace(".cond", ".end");
                int endLine = lines.size();
                
                if (labelToLine.containsKey(endLabel)) {
                    endLine = labelToLine.get(endLabel);
                } else {
                    // 尝试其他变体
                    for (String possibleEnd : labelToLine.keySet()) {
                        if ((possibleEnd.contains("_next") || possibleEnd.contains("_end") ||
                             possibleEnd.contains(".end")) && 
                            labelToLine.get(possibleEnd) > condLine) {
                            endLine = Math.min(endLine, labelToLine.get(possibleEnd));
                        }
                    }
                }
                
                if (endLine > condLine) {
                    loopRanges.add(new int[]{condLine, endLine});
                }
            }
        }
        
        // 计算每行深度
        for (int i = 0; i < lines.size(); i++) {
            for (int[] range : loopRanges) {
                if (i >= range[0] && i < range[1]) {
                    depth[i]++;
                }
            }
        }
        
        return depth;
    }
    
    // 活跃区间辅助类
    private static class LiveInterval {
        String var;
        int start;
        int end;
        boolean crossesCall;
        int maxLoopDepth;       // 最大循环深度
        long spillCost;         // 溢出代价（加权使用次数）
        String assignedReg;
    }

    private int processFunction(int startIndex) {
        stackOffsetMap = new HashMap<>();
        defLines = new HashMap<>();
        
        // 预留 RA 和 Old FP 的空间 (8字节)
        // 约定：$fp 指向栈帧顶部（旧 $sp），$ra 存 $fp-4, old_fp 存 $fp-8
        // 局部变量从 $fp-12 开始分配
        int currentOffset = -8; 

        String defineLine = llvmLines.get(startIndex).trim();
        currentFunctionName = defineLine.split("@")[1].split("\\(")[0];

        String argsPart = defineLine.substring(defineLine.indexOf('(') + 1, defineLine.lastIndexOf(')'));
        String[] params = argsPart.isEmpty() ? new String[0] : argsPart.split(",");
        
        // 参数处理：
        // 前4个参数 ($a0-$a3) 需要在栈帧内分配空间以便 spill
        // 第5个及后续参数位于 Caller 的栈帧中，偏移量为 0($fp), 4($fp)...
        for (int k = 0; k < params.length; k++) {
            String pName = params[k].trim().split(" ")[1];
            if (k < 4) {
                currentOffset -= 4;
                stackOffsetMap.put(pName, currentOffset);
            } else {
                // 参数 4 在 0($fp), 参数 5 在 4($fp)
                stackOffsetMap.put(pName, (k - 4) * 4);
            }
        }

        // 扫描函数体收集定义信息
        List<String> functionBodyLines = new ArrayList<>();
        int i;
        for (i = startIndex + 1; i < llvmLines.size(); i++) {
            String line = llvmLines.get(i).trim();
            if (line.equals("}")) break;
            if (line.equals("{")) continue;
            functionBodyLines.add(line);

            if (line.contains("=") && !line.trim().startsWith(";")) {
                String target = line.split("=")[0].trim();
                defLines.put(target, line);
            }
        }

        // --- 全局寄存器分配 (先分配寄存器，再决定哪些需要栈空间) ---
        allocateRegisters(functionBodyLines);
        
        // --- SAFE MODE: 完全禁用栈槽位复用 ---
        // 为每个变量分配独立的栈空间，不做任何复用
        // 对于 main 函数中的大数组，提升到 .data 段以避免栈溢出
        
        for (String line : functionBodyLines) {
            if (line.contains("=") && !line.trim().startsWith(";")) {
                String target = line.split("=")[0].trim();
                
                // Skip if already has stack allocation (e.g., parameters)
                if (stackOffsetMap.containsKey(target)) {
                    continue;
                }
                
                // Skip if allocated to register (and not alloca)
                boolean isAlloca = line.contains("alloca");
                if (registerMap.containsKey(target) && !isAlloca) {
                    continue;
                }
                
                // Calculate required size
                int sizeToAlloc = 4;
                if (line.contains("alloca [")) {
                    try {
                        int start = line.indexOf('[') + 1;
                        int end = line.indexOf(']');
                        String sizeStr = line.substring(start, end).split("x")[0].trim();
                        sizeToAlloc = Integer.parseInt(sizeStr) * 4;
                        // Handle zero-size arrays (bug in frontend IR generation)
                        // Always allocate at least 4 bytes to avoid offset collision
                        if (sizeToAlloc == 0) {
                            sizeToAlloc = 4;
                        }
                    } catch (Exception e) {}
                }
                
                // --- Static Promotion for Large Arrays in Main ---
                // If this is main function and array is large (>= 100 bytes = 25 ints),
                // promote to .data section to avoid stack overflow
                if (isAlloca && sizeToAlloc >= 100 && currentFunctionName.equals("main")) {
                    String staticName = "main_static_arr_" + (staticArrayCounter++);
                    dataSection.append(".align 2\n");
                    dataSection.append(staticName).append(": .space ").append(sizeToAlloc).append("\n");
                    // Store the label name with special marker for static arrays
                    // We'll handle this in code generation by using la instead of addiu
                    stackOffsetMap.put(target, Integer.MIN_VALUE); // Special marker
                    defLines.put(target, "STATIC:" + staticName); // Override definition
                    continue;
                }
                
                // --- Normal Stack Allocation (NO REUSE) ---
                currentOffset -= sizeToAlloc;
                stackOffsetMap.put(target, currentOffset);
            }
        }

        // 计算保存 $s 寄存器所需的空间
        // 将它们保存在局部变量之后
        int savedRegsStartOffset = currentOffset;
        int savedRegsSize = usedSRegs.size() * 4;
        currentOffset -= savedRegsSize;

        // FrameSize 必须是正数，用于 subu $sp
        frameSize = -currentOffset;
        // 严格确保 8 字节对齐 (MIPS ABI 要求)
        if (frameSize % 8 != 0) {
            frameSize += (8 - (frameSize % 8));
        }

        textSection.append("\n# ---- Function: ").append(currentFunctionName).append(" ----\n");
        textSection.append(currentFunctionName).append(":\n");

        // --- Prologue (核心修正) ---
        textSection.append("  # --- Prologue ---\n");
        textSection.append("  subu $sp, $sp, ").append(frameSize).append("\n"); // 开辟栈空间
        textSection.append("  sw $ra, ").append(frameSize - 4).append("($sp)\n"); // 存 RA 到顶端槽位
        textSection.append("  sw $fp, ").append(frameSize - 8).append("($sp)\n"); // 存 Old FP
        textSection.append("  addiu $fp, $sp, ").append(frameSize).append("\n");   // $fp 指向旧栈顶 (High Address)

        // 保存寄存器参数 ($a0-$a3) 到分配的负偏移槽位
        for (int k = 0; k < params.length && k < 4; k++) {
            String pName = params[k].trim().split(" ")[1];
            int offset = stackOffsetMap.get(pName);
            textSection.append("  sw $a").append(k).append(", ").append(offset).append("($fp)\n");
        }

        // 保存被使用的 $s 寄存器
        int tempOffset = savedRegsStartOffset;
        for (int r = 0; r < 8; r++) {
            String reg = "$s" + r;
            if (usedSRegs.contains(reg)) {
                tempOffset -= 4;
                textSection.append("  sw ").append(reg).append(", ").append(tempOffset).append("($fp)\n");
            }
        }

        // 如果参数被分配到了寄存器，需要将参数值装入寄存器
        for (int k = 0; k < params.length; k++) {
            String pName = params[k].trim().split(" ")[1];
            if (registerMap.containsKey(pName)) {
                String reg = registerMap.get(pName);
                if (k < 4) {
                    textSection.append("  move ").append(reg).append(", $a").append(k).append("\n");
                } else {
                    int offset = stackOffsetMap.get(pName);
                    textSection.append("  lw ").append(reg).append(", ").append(offset).append("($fp)\n");
                }
            }
        }

        for (String line : functionBodyLines) {
            if (line.endsWith(":")) {
                String labelName = line.substring(0, line.length() - 1);
                textSection.append(currentFunctionName).append("_").append(labelName).append(":\n");
            } else {
                parseInstruction(line);
            }
        }

        // --- Epilogue ---
        textSection.append("\n").append(currentFunctionName).append("_epilogue:\n");
        
        // 恢复 $s 寄存器
        tempOffset = savedRegsStartOffset;
        for (int r = 0; r < 8; r++) {
            String reg = "$s" + r;
            if (usedSRegs.contains(reg)) {
                tempOffset -= 4;
                textSection.append("  lw ").append(reg).append(", ").append(tempOffset).append("($fp)\n");
            }
        }

        textSection.append("  lw $ra, ").append(frameSize - 4).append("($sp)\n");
        textSection.append("  lw $fp, ").append(frameSize - 8).append("($sp)\n");
        textSection.append("  addiu $sp, $sp, ").append(frameSize).append("\n");
        textSection.append("  jr $ra\n");

        return i;
    }

    private void parseInstruction(String line) {
        if (line.isEmpty()) return;
        textSection.append("  # ").append(line).append("\n");
        String[] parts = line.replaceAll(",", "").split("\s+");

        String command = parts[0];
        if (line.contains("=")) command = parts[2];
        // 针对 phi 指令特殊处理 (如果还有残留)
        if (command.equals("phi")) return; 

        switch (command) {
            case "alloca": {
                // 如果 alloca 的结果变量被分配到了寄存器，需要初始化该寄存器为栈地址
                // Note: alloca results should always have stack slots (they represent addresses)
                String destReg = parts[0];
                if (registerMap.containsKey(destReg) && stackOffsetMap.containsKey(destReg)) {
                    int offset = stackOffsetMap.get(destReg);
                    String sReg = registerMap.get(destReg);
                    textSection.append("  addiu ").append(sReg).append(", $fp, ").append(offset).append("\n");
                }
                break; 
            }

            case "store": {
                String valueToStore = parts[2];
                String destPtr = parts[parts.length - 1];
                
                if (stackOffsetMap.get(destPtr) == null && !destPtr.startsWith("@") && !registerMap.containsKey(destPtr)) {
                    System.err.println("Error in store: destPtr='" + destPtr + "' not found in stackOffsetMap. Function: " + currentFunctionName + " Line: " + line);
                    System.err.println("Map keys: " + stackOffsetMap.keySet());
                }

                loadOperandToReg(valueToStore, getTempReg(0));
                
                if (destPtr.startsWith("@")) {
                    String globalName = destPtr.substring(1).replace('.', '_');
                    textSection.append("  la ").append(getTempReg(1)).append(", ").append(globalName).append("\n");
                    textSection.append("  sw ").append(getTempReg(0)).append(", 0(").append(getTempReg(1)).append(")\n");
                } else {
                    // 检查指针变量是否在寄存器中
                    if (registerMap.containsKey(destPtr)) {
                        String ptrReg = registerMap.get(destPtr);
                        textSection.append("  sw ").append(getTempReg(0)).append(", 0(").append(ptrReg).append(")\n");
                    } else {
                        String destDef = defLines.get(destPtr);
                        if (destDef != null && destDef.contains("alloca")) {
                            // 写入局部变量槽位: offset($fp)
                            int offset = stackOffsetMap.get(destPtr);
                            textSection.append("  sw ").append(getTempReg(0)).append(", ").append(offset).append("($fp)\n");
                        } else {
                            // 写入指针变量指向的地址: 需要先加载指针值
                            int ptrOffset = stackOffsetMap.get(destPtr);
                            textSection.append("  lw ").append(getTempReg(1)).append(", ").append(ptrOffset).append("($fp)\n");
                            textSection.append("  sw ").append(getTempReg(0)).append(", 0(").append(getTempReg(1)).append(")\n");
                        }
                    }
                }
                break;
            }
            case "load": {
                String destReg = parts[0];
                String srcPtr = parts[parts.length - 1];
                
                if (srcPtr.startsWith("@")) {
                    String globalName = srcPtr.substring(1).replace('.', '_');
                    textSection.append("  la ").append(getTempReg(0)).append(", ").append(globalName).append("\n");
                    textSection.append("  lw ").append(getTempReg(1)).append(", 0(").append(getTempReg(0)).append(")\n");
                } else {
                    // 检查源指针是否在寄存器中
                    if (registerMap.containsKey(srcPtr)) {
                        String ptrReg = registerMap.get(srcPtr);
                        textSection.append("  lw ").append(getTempReg(1)).append(", 0(").append(ptrReg).append(")\n");
                    } else {
                        String srcDef = defLines.get(srcPtr);
                        if (srcDef != null && srcDef.contains("alloca")) {
                            // 读取局部变量
                            int offset = stackOffsetMap.get(srcPtr);
                            textSection.append("  lw ").append(getTempReg(1)).append(", ").append(offset).append("($fp)\n");
                        } else {
                            // 读取指针指向的值
                            int ptrOffset = stackOffsetMap.get(srcPtr);
                            textSection.append("  lw ").append(getTempReg(0)).append(", ").append(ptrOffset).append("($fp)\n");
                            textSection.append("  lw ").append(getTempReg(1)).append(", 0(").append(getTempReg(0)).append(")\n");
                        }
                    }
                }
                
                // 存储结果
                if (registerMap.containsKey(destReg)) {
                    textSection.append("  move ").append(registerMap.get(destReg)).append(", ").append(getTempReg(1)).append("\n");
                } else {
                    int destOffset = stackOffsetMap.get(destReg);
                    textSection.append("  sw ").append(getTempReg(1)).append(", ").append(destOffset).append("($fp)\n");
                }
                break;
            }
            case "getelementptr": {
                String destReg = parts[0];
                
                // 简单解析：base 和 index
                // %dest = gep type, type* %base, i32 idx...
                String baseName = "";
                String indexName = "0";
                
                // 寻找 base (第一个非结果的 % 或 @ 开头的操作数)
                for(int j=0; j<parts.length; j++) {
                    String p = parts[j];
                    if (p.equals("getelementptr")) continue;
                    if (j == 0) continue; // Skip result variable
                    
                    if (p.startsWith("%") || p.startsWith("@")) {
                        baseName = p;
                        // 寻找 index (最后一个操作数)
                        // 注意：这里假设是简单的 GEP，最后一个是 index
                        // 对于 getelementptr i32, i32* %ptr, i32 0, i32 %idx
                        // 最后一个是 %idx
                        if (j < parts.length - 1) {
                             indexName = parts[parts.length-1];
                        }
                        break;
                    }
                }
                
                // Step 1: 计算 Base Address -> $t0
                if (baseName.startsWith("@")) {
                    String gName = baseName.substring(1).replace('.', '_');
                    textSection.append("  la ").append(getTempReg(0)).append(", ").append(gName).append("\n");
                } else {
                    if (registerMap.containsKey(baseName)) {
                        textSection.append("  move ").append(getTempReg(0)).append(", ").append(registerMap.get(baseName)).append("\n");
                    } else {
                        String def = defLines.get(baseName);
                        int offset = stackOffsetMap.get(baseName);
                        
                        // Check for statically promoted arrays
                        if (def != null && def.startsWith("STATIC:")) {
                            String staticLabel = def.substring(7);
                            textSection.append("  la ").append(getTempReg(0)).append(", ").append(staticLabel).append("\n");
                        } else if (def != null && def.contains("alloca")) {
                            // 局部数组：地址是 $fp + offset
                            textSection.append("  addi ").append(getTempReg(0)).append(", $fp, ").append(offset).append("\n");
                        } else {
                            // 指针变量：地址是 Mem[$fp + offset]
                            textSection.append("  lw ").append(getTempReg(0)).append(", ").append(offset).append("($fp)\n");
                        }
                    }
                }
                
                // Step 2: 计算 Index -> $t1
                if (indexName.startsWith("%")) {
                    loadOperandToReg(indexName, getTempReg(1));
                } else {
                    textSection.append("  li ").append(getTempReg(1)).append(", ").append(indexName).append("\n");
                }
                
                // Step 3: $t0 = $t0 + $t1 * 4
                textSection.append("  sll ").append(getTempReg(2)).append(", ").append(getTempReg(1)).append(", 2\n");
                textSection.append("  addu ").append(getTempReg(3)).append(", ").append(getTempReg(0)).append(", ").append(getTempReg(2)).append("\n");
                
                // Store result
                if (registerMap.containsKey(destReg)) {
                    textSection.append("  move ").append(registerMap.get(destReg)).append(", ").append(getTempReg(3)).append("\n");
                } else {
                    int destOffset = stackOffsetMap.get(destReg);
                    textSection.append("  sw ").append(getTempReg(3)).append(", ").append(destOffset).append("($fp)\n");
                }
                break;
            }
            case "add": case "sub": {
                String destReg = parts[0];
                String op1 = parts[parts.length-2];
                String op2 = parts[parts.length-1];

                String targetReg = registerMap.getOrDefault(destReg, getTempReg(2));

                // 使用 ensureInReg 避免不必要的 move
                String op1Reg = ensureInReg(op1, getTempReg(0));

                boolean optimized = false;
                // Try immediate optimization
                if (command.equals("add")) {
                    try {
                        int imm = Integer.parseInt(op2);
                        if (imm >= -32768 && imm <= 32767) {
                            textSection.append("  addiu ").append(targetReg).append(", ").append(op1Reg).append(", ").append(imm).append("\n");
                            optimized = true;
                        }
                    } catch (Exception e) {}
                } else { // sub
                    try {
                        int imm = Integer.parseInt(op2);
                        int negImm = -imm;
                        if (negImm >= -32768 && negImm <= 32767) {
                            textSection.append("  addiu ").append(targetReg).append(", ").append(op1Reg).append(", ").append(negImm).append("\n");
                            optimized = true;
                        }
                    } catch (Exception e) {}
                }

                if (!optimized) {
                    String op2Reg = ensureInReg(op2, getTempReg(1));
                    if (command.equals("add")) {
                        textSection.append("  addu ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                    } else {
                        textSection.append("  subu ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                    }
                }

                if (!registerMap.containsKey(destReg)) {
                    textSection.append("  sw ").append(targetReg).append(", ").append(stackOffsetMap.get(destReg)).append("($fp)\n");
                }
                break;
            }
            // === 2的幂次乘法优化 ===
            case "mul": {
                String destReg = parts[0];
                String op1 = parts[parts.length-2];
                String op2 = parts[parts.length-1];
                
                String targetReg = registerMap.getOrDefault(destReg, getTempReg(2));
                
                // 1. 规范化：如果 op1 是常数，交换到 op2，方便统一处理
                if (op1.matches("-?\\d+") && !op2.matches("-?\\d+")) {
                    String tmp = op1; op1 = op2; op2 = tmp;
                }

                // 使用 ensureInReg 避免不必要的 move
                String op1Reg = ensureInReg(op1, getTempReg(0));
                boolean optimized = false;

                try {
                    int imm = Integer.parseInt(op2);
                    // 使用 MulOptimizer 进行优化
                    java.util.List<String> optCode = MulOptimizer.generateOptimizedMul(
                        op1Reg, targetReg, imm, 
                        new String[]{getTempReg(1), getTempReg(3)});
                    
                    if (optCode != null) {
                        for (String codeLine : optCode) {
                            textSection.append(codeLine).append("\n");
                        }
                        optimized = true;
                    }
                } catch (Exception e) {}

                if (!optimized) {
                    String op2Reg = ensureInReg(op2, getTempReg(1));
                    textSection.append("  mul ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                }
                
                if (!registerMap.containsKey(destReg)) {
                    textSection.append("  sw ").append(targetReg).append(", ").append(stackOffsetMap.get(destReg)).append("($fp)\n");
                }
                break;
            }
            case "sdiv": {
                String destReg = parts[0];
                String op1 = parts[parts.length-2];
                String op2 = parts[parts.length-1];

                // 1. 确定目标寄存器
                String targetReg;
                if (registerMap.containsKey(destReg)) {
                    targetReg = registerMap.get(destReg);
                } else {
                    targetReg = getTempReg(2);
                }

                // 2. 使用 ensureInReg 加载被除数
                String op1Reg = ensureInReg(op1, getTempReg(0));

                boolean optimized = false;
                
                // 3. 尝试常量除法优化
                try {
                    int div = Integer.parseInt(op2);
                    // 排除 0, 1, -1 (1和-1通常不需要Magic Number，0是未定义行为)
                    if (div != 0 && div != 1 && div != -1) {
                        int absDiv = Math.abs(div);

                        // --- 优化 A: 除数为 2 的幂 ---
                        if ((absDiv & (absDiv - 1)) == 0) {
                            int k = Integer.numberOfTrailingZeros(absDiv);
                            // 核心公式: (x + (x >> 31 >>> (32-k))) >> k
                            // 取符号位: $t1 = x >> 31
                            textSection.append("  sra ").append(getTempReg(1)).append(", ").append(op1Reg).append(", 31\n");
                            if (k > 0) {
                                // $t1 = $t1 >>> (32-k)
                                textSection.append("  srl ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(32 - k).append("\n");
                            }
                            // $t1 = x + $t1
                            textSection.append("  addu ").append(getTempReg(1)).append(", ").append(op1Reg).append(", ").append(getTempReg(1)).append("\n");
                            // target = $t1 >> k
                            textSection.append("  sra ").append(targetReg).append(", ").append(getTempReg(1)).append(", ").append(k).append("\n");
                            
                            // 如果除数是负数，结果取反
                            if (div < 0) {
                                textSection.append("  subu ").append(targetReg).append(", $zero, ").append(targetReg).append("\n");
                            }
                            optimized = true;
                        } 
                        // --- 优化 B: Magic Number (处理非 2 的幂) ---
                        else {
                            // 复制被除数到临时寄存器，避免破坏
                            String dividendReg = getTempReg(0);
                            if (!op1Reg.equals(dividendReg)) {
                                textSection.append("  move ").append(dividendReg).append(", ").append(op1Reg).append("\n");
                            }
                            
                            // 获取魔数和移位量
                            MagicNumber mag = MagicNumber.getMagicNumber(div);
                            
                            // 加载魔数到 $t1
                            textSection.append("  li ").append(getTempReg(1)).append(", ").append(mag.multiplier).append("\n");
                            
                            // 执行乘法: (被除数 * 魔数) -> Hi, Lo
                            textSection.append("  mult ").append(dividendReg).append(", ").append(getTempReg(1)).append("\n");
                            
                            // 取高位 -> $t1
                            textSection.append("  mfhi ").append(getTempReg(1)).append("\n");
                            
                            // 此时 $t1 近似等于 q (商)，根据算法修正
                            if (div > 0 && mag.multiplier < 0) {
                                textSection.append("  addu ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(dividendReg).append("\n");
                            } else if (div < 0 && mag.multiplier > 0) {
                                textSection.append("  subu ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(dividendReg).append("\n");
                            }
                            
                            // 执行移位
                            if (mag.shift > 0) {
                                textSection.append("  sra ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(mag.shift).append("\n");
                            }
                            
                            // 符号位修正: q += (dividend < 0 ? 1 : 0) -> q += (dividend >>> 31)
                            textSection.append("  srl ").append(dividendReg).append(", ").append(dividendReg).append(", 31\n");
                            textSection.append("  addu ").append(targetReg).append(", ").append(getTempReg(1)).append(", ").append(dividendReg).append("\n");
                            
                            optimized = true;
                        }
                    }
                } catch (NumberFormatException e) { 
                    // op2 不是常数，无法优化
                }

                // 4. 兜底策略：生成普通 div 指令
                if (!optimized) {
                    String dividendReg = op1Reg.equals(getTempReg(0)) ? op1Reg : getTempReg(0);
                    if (!op1Reg.equals(dividendReg)) {
                        textSection.append("  move ").append(dividendReg).append(", ").append(op1Reg).append("\n");
                    }
                    String op2Reg = ensureInReg(op2, getTempReg(1));
                    textSection.append("  div ").append(dividendReg).append(", ").append(op2Reg).append("\n");
                    textSection.append("  mflo ").append(targetReg).append("\n");
                }

                // 5. 写回栈（如果没分配寄存器）
                if (!registerMap.containsKey(destReg)) {
                    textSection.append("  sw ").append(targetReg).append(", ").append(stackOffsetMap.get(destReg)).append("($fp)\n");
                }
                break;
            }
            // === 取模优化：支持所有常数 ===
            case "srem": {
                String destReg = parts[0];
                String op1 = parts[parts.length-2];
                String op2 = parts[parts.length-1];
                
                String targetReg;
                if (registerMap.containsKey(destReg)) {
                    targetReg = registerMap.get(destReg);
                } else {
                    targetReg = getTempReg(2);
                }
                
                // 使用 ensureInReg 避免不必要的 move
                String op1Reg = ensureInReg(op1, getTempReg(0));
                
                boolean optimized = false;
                try {
                    int div = Integer.parseInt(op2);
                    int absDiv = Math.abs(div);
                    
                    // 特殊情况：div = 1 或 -1，余数为 0
                    if (div == 1 || div == -1) {
                        textSection.append("  move ").append(targetReg).append(", $zero\n");
                        optimized = true;
                    }
                    // 优化 A: 2 的幂次
                    else if (div != 0 && (absDiv & (absDiv - 1)) == 0) {
                        int k = Integer.numberOfTrailingZeros(absDiv);
                        // r = a - (a / d) * d
                        textSection.append("  sra ").append(getTempReg(1)).append(", ").append(op1Reg).append(", 31\n");
                        if (k > 0) {
                            textSection.append("  srl ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(32 - k).append("\n");
                        }
                        textSection.append("  addu ").append(getTempReg(1)).append(", ").append(op1Reg).append(", ").append(getTempReg(1)).append("\n");
                        textSection.append("  sra ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(k).append("\n");
                        textSection.append("  sll ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(k).append("\n");
                        textSection.append("  subu ").append(targetReg).append(", ").append(op1Reg).append(", ").append(getTempReg(1)).append("\n");
                        optimized = true;
                    }
                    // 优化 B: 非 2 的幂次，使用 Magic Number
                    // r = a - (a / d) * d
                    else if (div != 0) {
                        // 复制到 $t0，并使用 $t8 保存原始被除数
                        String dividendReg = getTempReg(0);
                        if (!op1Reg.equals(dividendReg)) {
                            textSection.append("  move ").append(dividendReg).append(", ").append(op1Reg).append("\n");
                        }
                        String saveDividend = "$t8";
                        textSection.append("  move ").append(saveDividend).append(", ").append(dividendReg).append("\n");
                        
                        // 计算 a / d 使用 Magic Number（结果在 $t1）
                        MagicNumber mag = MagicNumber.getMagicNumber(div);
                        
                        // 加载魔数
                        textSection.append("  li ").append(getTempReg(1)).append(", ").append(mag.multiplier).append("\n");
                        
                        // 执行乘法
                        textSection.append("  mult ").append(dividendReg).append(", ").append(getTempReg(1)).append("\n");
                        textSection.append("  mfhi ").append(getTempReg(1)).append("\n");
                        
                        // 修正
                        if (div > 0 && mag.multiplier < 0) {
                            textSection.append("  addu ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(dividendReg).append("\n");
                        } else if (div < 0 && mag.multiplier > 0) {
                            textSection.append("  subu ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(dividendReg).append("\n");
                        }
                        
                        // 移位
                        if (mag.shift > 0) {
                            textSection.append("  sra ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(mag.shift).append("\n");
                        }
                        
                        // 符号位修正: q += (dividend >>> 31)
                        textSection.append("  srl ").append(dividendReg).append(", ").append(dividendReg).append(", 31\n");
                        textSection.append("  addu ").append(getTempReg(1)).append(", ").append(getTempReg(1)).append(", ").append(dividendReg).append("\n");
                        // 现在 $t1 = a / d (商)
                        
                        // 计算 (a / d) * absDiv，尝试使用乘法优化
                        // 注意：srcReg=$t1, dstReg=$t0, tempRegs 不能包含 dstReg
                        java.util.List<String> mulCode = MulOptimizer.generateOptimizedMul(
                            getTempReg(1), getTempReg(0), absDiv, new String[]{"$t9", "$t3"});
                        if (mulCode != null) {
                            for (String mulLine : mulCode) {
                                textSection.append(mulLine).append("\n");
                            }
                        } else {
                            textSection.append("  li ").append(getTempReg(0)).append(", ").append(absDiv).append("\n");
                            textSection.append("  mul ").append(getTempReg(0)).append(", ").append(getTempReg(1)).append(", ").append(getTempReg(0)).append("\n");
                        }
                        
                        // r = a - (a / d) * absDiv
                        textSection.append("  subu ").append(targetReg).append(", ").append(saveDividend).append(", ").append(getTempReg(0)).append("\n");
                        optimized = true;
                    }
                } catch (NumberFormatException e) { /* 不是常数，跳过优化 */ }
                
                if (!optimized) {
                    // 对于 fallback 路径，需要确保被除数在 $t0
                    String dividendReg = op1Reg.equals(getTempReg(0)) ? op1Reg : getTempReg(0);
                    if (!op1Reg.equals(dividendReg)) {
                        textSection.append("  move ").append(dividendReg).append(", ").append(op1Reg).append("\n");
                    }
                    String op2Reg = ensureInReg(op2, getTempReg(1));
                    textSection.append("  div ").append(dividendReg).append(", ").append(op2Reg).append("\n");
                    textSection.append("  mfhi ").append(targetReg).append("\n");
                }
                
                if (!registerMap.containsKey(destReg)) {
                    textSection.append("  sw ").append(targetReg).append(", ").append(stackOffsetMap.get(destReg)).append("($fp)\n");
                }
                break;
            }
            case "icmp": {
                String destReg = parts[0];
                String cond = parts[3];
                String op1 = parts[5];
                String op2 = parts[6];
                
                // Determine target register for result
                String targetReg;
                if (registerMap.containsKey(destReg)) {
                    targetReg = registerMap.get(destReg);
                } else {
                    targetReg = getTempReg(2);
                }
                
                // 使用 ensureInReg 优化
                String op1Reg = ensureInReg(op1, getTempReg(0));
                String op2Reg = ensureInReg(op2, getTempReg(1));
                
                if (cond.equals("eq")) {
                    // seq $t2, $t0, $t1 -> xor $t2, $t0, $t1; sltiu $t2, $t2, 1
                    textSection.append("  xor ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                    textSection.append("  sltiu ").append(targetReg).append(", ").append(targetReg).append(", 1\n");
                } else if (cond.equals("ne")) {
                    // sne $t2, $t0, $t1 -> xor $t2, $t0, $t1; sltu $t2, $zero, $t2
                    textSection.append("  xor ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                    textSection.append("  sltu ").append(targetReg).append(", $zero, ").append(targetReg).append("\n");
                } else if (cond.equals("slt")) {
                    textSection.append("  slt ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                } else if (cond.equals("sle")) {
                    textSection.append("  sle ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                } else if (cond.equals("sgt")) {
                    textSection.append("  sgt ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                } else if (cond.equals("sge")) {
                    textSection.append("  sge ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
                }
                
                // Only store to stack if not in register
                if (!registerMap.containsKey(destReg)) {
                    textSection.append("  sw ").append(targetReg).append(", ").append(stackOffsetMap.get(destReg)).append("($fp)\n");
                }
                break;
            }
            case "zext": {
                String destReg = parts[0];
                String srcReg = parts[4];
                
                // If destReg is in a register, load directly into it
                if (registerMap.containsKey(destReg)) {
                    loadOperandToReg(srcReg, registerMap.get(destReg));
                } else {
                    loadOperandToReg(srcReg, getTempReg(0));
                    textSection.append("  sw ").append(getTempReg(0)).append(", ").append(stackOffsetMap.get(destReg)).append("($fp)\n");
                }
                break;
            }
            case "br": {
                if (parts.length > 4) { // Conditional
                    String condReg = parts[2];
                    String l1 = parts[4].replace("%", "");
                    String l2 = parts[6].replace("%", "");
                    
                    loadOperandToReg(condReg, getTempReg(0));
                    textSection.append("  beq ").append(getTempReg(0)).append(", $zero, ").append(currentFunctionName).append("_").append(l2).append("\n");
                    textSection.append("  j ").append(currentFunctionName).append("_").append(l1).append("\n");
                } else { // Unconditional
                    String l = parts[2].replace("%", "");
                    textSection.append("  j ").append(currentFunctionName).append("_").append(l).append("\n");
                }
                break;
            }
            case "call": {
                String funcName = "";
                for(String p : parts) if(p.startsWith("@")) { funcName = p.substring(1).split("\\(")[0]; break; }
                
                // === Printf 内联展开优化 ===
                if (funcName.equals("printf")) {
                    // 1. 提取格式化字符串标签
                    String fmtLabel = "";
                    for (String p : parts) {
                        if (p.startsWith("@_str_") || p.startsWith("@.str.") || p.startsWith("@.str")) {
                            fmtLabel = p.replace("@", "").replace(",", "").split("\\)")[0];
                            break;
                        }
                    }
                    
                    // 2. 提取参数列表
                    List<String> printArgs = new ArrayList<>();
                    boolean foundFmt = false;
                    for (String p : parts) {
                        if (p.contains("@_str_") || p.contains("@.str")) { 
                            foundFmt = true; 
                            continue; 
                        }
                        if (foundFmt) {
                            String arg = p.replace(")", "").replace(",", "").trim();
                            if (arg.equals("i32") || arg.isEmpty()) continue;
                            if (arg.startsWith("%") || arg.matches("-?\\d+")) {
                                printArgs.add(arg);
                            }
                        }
                    }
                    
                    // 3. 查找字符串内容并内联展开
                    String mipsLabelKey = fmtLabel.replace('.', '_');
                    String fmtContent = stringMap.get(mipsLabelKey);
                    
                    if (fmtContent != null) {
                        int argIndex = 0;
                        int len = fmtContent.length();
                        for (int k = 0; k < len; k++) {
                            char c = fmtContent.charAt(k);
                            if (c == '%' && k + 1 < len && fmtContent.charAt(k + 1) == 'd') {
                                // 输出整数
                                if (argIndex < printArgs.size()) {
                                    loadOperandToReg(printArgs.get(argIndex), "$a0");
                                    textSection.append("  li $v0, 1\n");
                                    textSection.append("  syscall\n");
                                    argIndex++;
                                }
                                k++; // 跳过 'd'
                            } else {
                                // 输出单个字符
                                int charCode = (int) c;
                                textSection.append("  li $a0, ").append(charCode).append("\n");
                                textSection.append("  li $v0, 11\n");
                                textSection.append("  syscall\n");
                            }
                        }
                        break; // printf 处理完毕
                    }
                }
                // === End Printf 内联优化 ===
                
                List<String> args = new ArrayList<>();
                int startIdx = -1;
                for(int k=0; k<parts.length; k++) if(parts[k].contains("(")) startIdx = k;
                if(startIdx != -1) {
                    for(int k=startIdx; k<parts.length; k++) {
                         String p = parts[k].replace(")", "").replace(",", "").replace("(", "");
                         if(p.startsWith("@") && parts[k].contains("(")) {
                             continue;
                         }
                         if(p.startsWith("%") || p.startsWith("@") || p.matches("-?\\d+")) args.add(p);
                    }
                }
                
                for(int k=0; k<args.size() && k<4; k++) {
                     loadOperandToReg(args.get(k), "$a" + k);
                }
                int extra = args.size() - 4;
                for(int k=args.size()-1; k>=4; k--) {
                    loadOperandToReg(args.get(k), getTempReg(0));
                    textSection.append("  addiu $sp, $sp, -4\n");
                    textSection.append("  sw ").append(getTempReg(0)).append(", 0($sp)\n");
                }
                
                textSection.append("  jal ").append(funcName).append("\n");
                
                if(extra > 0) textSection.append("  addiu $sp, $sp, ").append(extra * 4).append("\n");
                
                if(parts[0].startsWith("%")) {
                    if (registerMap.containsKey(parts[0])) {
                        textSection.append("  move ").append(registerMap.get(parts[0])).append(", $v0\n");
                    } else {
                        textSection.append("  sw $v0, ").append(stackOffsetMap.get(parts[0])).append("($fp)\n");
                    }
                }
                break;
            }
            case "ret": {
                 if(parts.length > 2) {
                     loadOperandToReg(parts[2], "$v0");
                 }
                 textSection.append("  j ").append(currentFunctionName).append("_epilogue\n");
                 break;
            }
            case "phi": break; // 忽略 phi，因为已经转为 CFG 
        }
    }

    private void appendRuntimeLibrary() {
        // getint
        textSection.append("\ngetint:\n  li $v0, 5\n  syscall\n  jr $ra\n");
        // printf (简化版，假设 MARS 环境)
        textSection.append("\nprintf:\n  move $t0, $a0\n  move $t1, $a1\n  move $t2, $a2\n  move $t3, $a3\n");
        textSection.append("  move $t5, $a0\n"); // format string
        textSection.append("  li $t0, 1\n"); // arg index
        textSection.append("printf_loop:\n");
        textSection.append("  lbu $t1, 0($t5)\n");
        textSection.append("  beq $t1, $zero, printf_end\n");
        textSection.append("  li $t2, 37\n"); // '%'
        textSection.append("  bne $t1, $t2, printf_char\n");
        
        // Check next char
        textSection.append("  lbu $t2, 1($t5)\n");
        
        // Check for %%
        textSection.append("  li $t3, 37\n"); // '%'
        textSection.append("  beq $t2, $t3, printf_percent\n");
        
        // Check for %d
        textSection.append("  li $t3, 100\n"); // 'd'
        textSection.append("  bne $t2, $t3, printf_char\n"); // Not %d, print %
        
        // Is %d
        textSection.append("  addi $t5, $t5, 2\n"); // Skip % and d
        
        textSection.append("  li $t3, 1\n  beq $t0, $t3, p_a1\n");
        textSection.append("  li $t3, 2\n  beq $t0, $t3, p_a2\n");
        textSection.append("  li $t3, 3\n  beq $t0, $t3, p_a3\n");
        textSection.append("  addi $t4, $t0, -4\n  sll $t4, $t4, 2\n  addu $t4, $sp, $t4\n  lw $a0, 0($t4)\n  j p_int\n");
        textSection.append("p_a1:\n  move $a0, $a1\n  j p_int\n");
        textSection.append("p_a2:\n  move $a0, $a2\n  j p_int\n");
        textSection.append("p_a3:\n  move $a0, $a3\n  j p_int\n");
        textSection.append("p_int:\n  li $v0, 1\n  syscall\n  addi $t0, $t0, 1\n  j printf_loop\n");
        
        textSection.append("printf_percent:\n  li $v0, 11\n  li $a0, 37\n  syscall\n  addi $t5, $t5, 2\n  j printf_loop\n");
        
        textSection.append("printf_char:\n  li $v0, 11\n  move $a0, $t1\n  syscall\n  addi $t5, $t5, 1\n  j printf_loop\n");
        textSection.append("printf_end:\n  jr $ra\n");
    }
}