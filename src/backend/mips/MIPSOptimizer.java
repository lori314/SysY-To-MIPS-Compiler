package backend.mips;

import java.util.*;

public class MIPSOptimizer {
    private List<String> lines;

    public MIPSOptimizer(String mipsCode) {
        this.lines = new ArrayList<>(Arrays.asList(mipsCode.split("\n")));
    }

    public String optimize() {
        boolean changed = true;
        int pass = 0;
        while (changed && pass < 10) {
            changed = false;
            changed |= eliminateLiZero();     // li $r, 0 -> move $r, $zero
            changed |= eliminateAddSubZero();  // add/sub 0 优化
            changed |= eliminateSllZero();     // sll $r, $zero, N -> move $r, $zero
            changed |= eliminateLoadMove();    // lw $t0, addr; move $t1, $t0 -> lw $t1, addr
            changed |= eliminateMoveChain();   // 连续 move 到同一目标的优化
            // eliminateMoveBeforeOp 暂时禁用，需要更仔细的实现
            // changed |= eliminateMoveBeforeOp(); // move $t0, $s0; op $d, $t0, ... -> op $d, $s0, ...
            changed |= eliminateRedundantLoadStore();
            // changed |= storeLoadForwarding();  // BUG: disabled due to incorrect forwarding
            changed |= optimizeBranches(); // slt + bne -> blt 等
            changed |= optimizeBranchJump(); // beq L1; j L2; L1: -> bne L2
            changed |= eliminateRedundantJumps();
            changed |= eliminateFallthroughJumps(); // 消除跳到下一行的j指令
            changed |= eliminateRedundantMoves();
            // changed |= propagateMoveAndEliminate(); // BUG: disabled
            pass++;
        }
        
        // 循环归纳变量寄存器化（只运行一次，在最后）
        // loopInductionVariableOptimization();  // BUG: disabled
        
        return String.join("\n", lines);
    }
    
    /**
     * 循环归纳变量寄存器化
     * 
     * 识别模式：
     * label:
     *   lw $t, offset($fp)  ; load 归纳变量
     *   ...
     *   addiu $t2, $t, 1    ; 更新
     *   sw $t2, offset($fp) ; store 归纳变量
     *   j label             ; 跳回循环头
     * 
     * 优化为：
     * label:
     *   ; $t 保持归纳变量值（首次进入前初始化）
     *   ...
     *   addiu $t, $t, 1
     *   j label
     */
    private boolean loopInductionVariableOptimization() {
        // 识别循环边界
        Map<String, Integer> labelToLine = new HashMap<>();
        Map<Integer, String> lineToJumpTarget = new HashMap<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.endsWith(":") && !line.startsWith("#")) {
                labelToLine.put(line.substring(0, line.length() - 1), i);
            }
            if (line.startsWith("j ") && !line.startsWith("jal ") && !line.startsWith("jr ")) {
                String target = line.substring(2).trim();
                lineToJumpTarget.put(i, target);
            }
        }
        
        // 找到回边（跳转到前面的标签）
        List<int[]> loops = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : lineToJumpTarget.entrySet()) {
            int jumpLine = entry.getKey();
            String target = entry.getValue();
            if (labelToLine.containsKey(target)) {
                int targetLine = labelToLine.get(target);
                if (targetLine < jumpLine) {
                    // 找到一个循环：targetLine 是循环头，jumpLine 是回边
                    loops.add(new int[]{targetLine, jumpLine});
                }
            }
        }
        
        if (loops.isEmpty()) return false;
        
        boolean changed = false;
        
        // 对每个循环，尝试优化归纳变量
        for (int[] loop : loops) {
            changed |= optimizeLoopInduction(loop[0], loop[1]);
        }
        
        return changed;
    }
    
    /**
     * 优化单个循环的归纳变量
     */
    private boolean optimizeLoopInduction(int loopStart, int loopEnd) {
        // 收集循环内的所有 load 和 store
        Map<String, List<int[]>> stackAccesses = new HashMap<>(); // addr -> [[line, isLoad], ...]
        
        for (int i = loopStart; i <= loopEnd; i++) {
            String line = lines.get(i).trim();
            String[] memInfo = parseMemInstr(line);
            if (memInfo != null && (memInfo[0].equals("lw") || memInfo[0].equals("sw"))) {
                String addr = memInfo[2];
                // 只处理栈变量（相对于 $fp 或 $sp）
                if (addr.contains("($fp)") || addr.contains("($sp)")) {
                    stackAccesses.computeIfAbsent(addr, k -> new ArrayList<>())
                                 .add(new int[]{i, memInfo[0].equals("lw") ? 1 : 0});
                }
            }
        }
        
        boolean changed = false;
        
        // 找出只有一个 load 和一个 store 的栈变量（归纳变量模式）
        for (Map.Entry<String, List<int[]>> entry : stackAccesses.entrySet()) {
            String addr = entry.getKey();
            List<int[]> accesses = entry.getValue();
            
            int loadCount = 0, storeCount = 0;
            int loadLine = -1, storeLine = -1;
            
            for (int[] access : accesses) {
                if (access[1] == 1) { // load
                    loadCount++;
                    loadLine = access[0];
                } else { // store
                    storeCount++;
                    storeLine = access[0];
                }
            }
            
            // 归纳变量模式：循环内有一个 load 和一个 store
            // load 在循环头附近，store 在回边之前
            if (loadCount == 1 && storeCount == 1 && loadLine < storeLine) {
                // 尝试将这个变量寄存器化
                changed |= registerizeInductionVar(addr, loadLine, storeLine, loopStart, loopEnd);
            }
        }
        
        return changed;
    }
    
    /**
     * 将归纳变量寄存器化
     */
    private boolean registerizeInductionVar(String addr, int loadLine, int storeLine, int loopStart, int loopEnd) {
        String loadInstr = lines.get(loadLine).trim();
        String storeInstr = lines.get(storeLine).trim();
        
        String[] loadParts = parseMemInstr(loadInstr);
        String[] storeParts = parseMemInstr(storeInstr);
        
        if (loadParts == null || storeParts == null) return false;
        
        String loadReg = loadParts[1];   // lw 的目标寄存器
        String storeReg = storeParts[1]; // sw 的源寄存器
        
        // 首先，追踪 storeReg 来自哪里（可能通过 move 链）
        String traceStoreReg = storeReg;
        for (int i = storeLine - 1; i >= loadLine; i--) {
            String line = lines.get(i).trim();
            if (line.startsWith("move ")) {
                String[] parts = line.split("[\\s,]+");
                if (parts.length >= 3 && parts[1].equals(traceStoreReg)) {
                    traceStoreReg = parts[2];
                }
            }
        }
        
        // 找到更新指令（addiu 的目标应该是 traceStoreReg）
        int updateLine = -1;
        String updateSrc = null;
        int increment = 0;
        
        for (int i = storeLine - 1; i >= loadLine; i--) {
            String line = lines.get(i).trim();
            if (line.startsWith("addiu ")) {
                String[] parts = line.split("[\\s,]+");
                if (parts.length >= 4 && parts[1].equals(traceStoreReg)) {
                    updateLine = i;
                    updateSrc = parts[2];
                    try {
                        increment = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    break;
                }
            }
        }
        
        if (updateLine == -1) return false;
        
        // 追踪 updateSrc 来自哪里
        String traceUpdateSrc = updateSrc;
        for (int i = updateLine - 1; i >= loadLine; i--) {
            String line = lines.get(i).trim();
            if (line.startsWith("move ")) {
                String[] parts = line.split("[\\s,]+");
                if (parts.length >= 3 && parts[1].equals(traceUpdateSrc)) {
                    traceUpdateSrc = parts[2];
                }
            }
        }
        
        // 确认最终追踪到 loadReg
        if (!traceUpdateSrc.equals(loadReg)) return false;
        
        // 归纳变量模式确认！
        // 模式：lw $loadReg, addr; move $X, $loadReg; ... addiu $Y, $X, inc; move $storeReg, $Y; sw $storeReg, addr
        
        // 优化策略：消除 load 后面的 move（如果有），让 load 直接加载到使用的寄存器
        if (loadLine + 1 < lines.size()) {
            String nextLine = lines.get(loadLine + 1).trim();
            if (nextLine.startsWith("move ")) {
                String[] moveParts = nextLine.split("[\\s,]+");
                if (moveParts.length >= 3 && moveParts[2].equals(loadReg)) {
                    // 模式：lw $t1, addr; move $s2, $t1
                    // 优化为：lw $s2, addr; # (optimized out)
                    String indent = getIndent(lines.get(loadLine));
                    lines.set(loadLine, indent + "lw " + moveParts[1] + ", " + addr);
                    lines.set(loadLine + 1, getIndent(lines.get(loadLine + 1)) + "# (optimized out)");
                    return true;
                }
            }
        }
        
        // 优化策略 2：消除 store 前面的 move（如果有）
        if (storeLine - 1 >= loopStart) {
            String prevLine = lines.get(storeLine - 1).trim();
            if (prevLine.startsWith("move ")) {
                String[] moveParts = prevLine.split("[\\s,]+");
                if (moveParts.length >= 3 && moveParts[1].equals(storeReg)) {
                    // 模式：move $t0, $s1; sw $t0, addr
                    // 优化为：# (optimized out); sw $s1, addr
                    String indent = getIndent(lines.get(storeLine));
                    lines.set(storeLine - 1, getIndent(lines.get(storeLine - 1)) + "# (optimized out)");
                    lines.set(storeLine, indent + "sw " + moveParts[2] + ", " + addr);
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * 优化 li $reg, 0 => move $reg, $zero
     * 这样可以用一条指令代替可能的 lui+ori 组合
     */
    private boolean eliminateLiZero() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.startsWith("li ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 3) {
                    String dst = parts[1];
                    String imm = parts[2];
                    
                    if (imm.equals("0")) {
                        // li $t0, 0 => move $t0, $zero
                        newLines.add(getIndent(line) + "move " + dst + ", $zero");
                        changed = true;
                        continue;
                    }
                }
            }
            
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }

    /**
     * 优化 add/sub 0 的情况:
     * addiu $t0, $t1, 0 => move $t0, $t1  (如果 $t0 != $t1)
     * addiu $t0, $t0, 0 => (删除)
     * addu $t0, $t1, $zero => move $t0, $t1
     * subu $t0, $t1, $zero => move $t0, $t1
     * 
     * 也追踪已知为零的寄存器来优化 addu $t0, $t1, <zero_reg>
     */
    private boolean eliminateAddSubZero() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        // 追踪哪些寄存器当前包含 $zero
        Set<String> zeroRegs = new HashSet<>();
        zeroRegs.add("$zero");
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // 更新零寄存器追踪
            if (trimmed.startsWith("move ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 3) {
                    String dst = parts[1];
                    String src = parts[2];
                    if (zeroRegs.contains(src)) {
                        zeroRegs.add(dst);
                    } else {
                        zeroRegs.remove(dst);
                    }
                }
            } else if (trimmed.startsWith("li ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 3 && parts[2].equals("0")) {
                    zeroRegs.add(parts[1]);
                } else if (parts.length >= 2) {
                    zeroRegs.remove(parts[1]);
                }
            } else if (trimmed.endsWith(":")) {
                zeroRegs.clear();
                zeroRegs.add("$zero");
            } else if (trimmed.startsWith("jal ")) {
                zeroRegs.removeIf(r -> r.startsWith("$t") || r.startsWith("$a") || r.startsWith("$v"));
            } else if (isDefiningInstruction(trimmed) && !trimmed.startsWith("addiu ") && 
                       !trimmed.startsWith("addu ") && !trimmed.startsWith("subu ")) {
                String dst = getDefReg(trimmed);
                if (dst != null) {
                    zeroRegs.remove(dst);
                }
            }
            
            // addiu $t0, $t1, 0
            if (trimmed.startsWith("addiu ") || trimmed.startsWith("addu ") || trimmed.startsWith("subu ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 4) {
                    String op = parts[0];
                    String dst = parts[1];
                    String src1 = parts[2];
                    String src2 = parts[3];
                    
                    boolean isSrc2Zero = src2.equals("0") || zeroRegs.contains(src2);
                    boolean isSrc1Zero = zeroRegs.contains(src1);
                    
                    if (isSrc2Zero) {
                        if (dst.equals(src1)) {
                            // addiu $t0, $t0, 0 => 删除
                            changed = true;
                            continue;
                        } else {
                            // addiu $t0, $t1, 0 => move $t0, $t1
                            newLines.add(getIndent(line) + "move " + dst + ", " + src1);
                            if (isSrc1Zero) {
                                zeroRegs.add(dst);
                            } else {
                                zeroRegs.remove(dst);
                            }
                            changed = true;
                            continue;
                        }
                    } else if (isSrc1Zero && (op.equals("addu") || op.equals("addiu"))) {
                        // addu $t0, $zero, $t1 => move $t0, $t1
                        // 注意：只有当 src2 是寄存器时才能转换！
                        // 如果 src2 是立即数（如 addiu $t0, $zero, -1），应该变成 li $t0, -1
                        if (src2.startsWith("$")) {
                            // src2 是寄存器
                            if (dst.equals(src2)) {
                                // 删除
                                changed = true;
                                continue;
                            } else {
                                newLines.add(getIndent(line) + "move " + dst + ", " + src2);
                                if (zeroRegs.contains(src2)) {
                                    zeroRegs.add(dst);
                                } else {
                                    zeroRegs.remove(dst);
                                }
                                changed = true;
                                continue;
                            }
                        } else {
                            // src2 是立即数，转换为 li
                            newLines.add(getIndent(line) + "li " + dst + ", " + src2);
                            zeroRegs.remove(dst);
                            changed = true;
                            continue;
                        }
                    }
                    
                    // 如果没有优化，更新追踪
                    zeroRegs.remove(dst);
                }
            }
            
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }

    /**
     * 优化 sll $t0, $zero, N => move $t0, $zero
     * 因为 0 << N = 0
     * 
     * 也处理: move $t1, $zero; sll $t2, $t1, N => move $t1, $zero; move $t2, $zero
     */
    private boolean eliminateSllZero() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        // 追踪哪些寄存器当前包含 $zero
        Set<String> zeroRegs = new HashSet<>();
        zeroRegs.add("$zero");
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // 检查 move $t, $zero 来追踪零寄存器
            if (trimmed.startsWith("move ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 3) {
                    String dst = parts[1];
                    String src = parts[2];
                    if (zeroRegs.contains(src)) {
                        zeroRegs.add(dst);
                    } else {
                        zeroRegs.remove(dst);
                    }
                }
            }
            // li $t, 0 也产生零
            else if (trimmed.startsWith("li ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 3 && parts[2].equals("0")) {
                    zeroRegs.add(parts[1]);
                } else if (parts.length >= 2) {
                    zeroRegs.remove(parts[1]);
                }
            }
            // 标签清除所有追踪（保守策略）
            else if (trimmed.endsWith(":")) {
                zeroRegs.clear();
                zeroRegs.add("$zero");
            }
            // 函数调用清除临时寄存器
            else if (trimmed.startsWith("jal ")) {
                zeroRegs.removeIf(r -> r.startsWith("$t") || r.startsWith("$a") || r.startsWith("$v"));
            }
            // 其他定义寄存器的指令
            else if (isDefiningInstruction(trimmed)) {
                String dst = getDefReg(trimmed);
                if (dst != null) {
                    zeroRegs.remove(dst);
                }
            }
            
            if (trimmed.startsWith("sll ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 4) {
                    String dst = parts[1];
                    String src = parts[2];
                    
                    if (zeroRegs.contains(src)) {
                        // sll $t0, <zero_reg>, N => move $t0, $zero
                        newLines.add(getIndent(line) + "move " + dst + ", $zero");
                        zeroRegs.add(dst);
                        changed = true;
                        continue;
                    } else {
                        zeroRegs.remove(dst);
                    }
                }
            }
            
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }
    
    private boolean isDefiningInstruction(String trimmed) {
        // 常见的定义目标寄存器的指令
        return trimmed.startsWith("lw ") || trimmed.startsWith("la ") || 
               trimmed.startsWith("add") || trimmed.startsWith("sub") ||
               trimmed.startsWith("mul") || trimmed.startsWith("div") ||
               trimmed.startsWith("sll ") || trimmed.startsWith("srl ") ||
               trimmed.startsWith("sra ") || trimmed.startsWith("and ") ||
               trimmed.startsWith("or ") || trimmed.startsWith("xor ") ||
               trimmed.startsWith("nor ") || trimmed.startsWith("slt") ||
               trimmed.startsWith("seq ") || trimmed.startsWith("sne ") ||
               trimmed.startsWith("sge ") || trimmed.startsWith("sle ") ||
               trimmed.startsWith("sgt ");
    }
    
    private String getDefReg(String trimmed) {
        // 获取指令定义的目标寄存器
        String[] parts = trimmed.split("[\\s,]+");
        if (parts.length >= 2 && parts[1].startsWith("$")) {
            return parts[1];
        }
        return null;
    }
    
    /**
     * 优化 lw/la $t0, addr; move $t1, $t0 => lw/la $t1, addr
     * 前提：$t0 之后没有被使用（这里保守地只处理紧邻的情况）
     */
    private boolean eliminateLoadMove() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // 处理 lw 指令
            if (trimmed.startsWith("lw ") && i + 1 < lines.size()) {
                String nextLine = lines.get(i + 1).trim();
                
                if (nextLine.startsWith("move ")) {
                    String[] lwParts = trimmed.split("[\\s,]+");
                    String[] moveParts = nextLine.split("[\\s,]+");
                    
                    if (lwParts.length >= 3 && moveParts.length >= 3) {
                        String lwDst = lwParts[1];
                        String lwAddr = lwParts[2];
                        String moveDst = moveParts[1];
                        String moveSrc = moveParts[2];
                        
                        // lw $t0, addr; move $t1, $t0 => lw $t1, addr
                        // 只有当 lw 的目标是 move 的源，且 lw 目标是临时寄存器时才优化
                        // 并且 lwDst 之后不再被使用
                        if (lwDst.equals(moveSrc) && lwDst.startsWith("$t") && !isRegUsedAfter(i + 2, lwDst)) {
                            // 合并为单条 lw 指令
                            newLines.add(getIndent(line) + "lw " + moveDst + ", " + lwAddr);
                            i++; // 跳过 move
                            changed = true;
                            continue;
                        }
                    }
                }
            }
            
            // 处理 la 指令
            if (trimmed.startsWith("la ") && i + 1 < lines.size()) {
                String nextLine = lines.get(i + 1).trim();
                
                if (nextLine.startsWith("move ")) {
                    String[] laParts = trimmed.split("[\\s,]+");
                    String[] moveParts = nextLine.split("[\\s,]+");
                    
                    if (laParts.length >= 3 && moveParts.length >= 3) {
                        String laDst = laParts[1];
                        String laAddr = laParts[2];
                        String moveDst = moveParts[1];
                        String moveSrc = moveParts[2];
                        
                        // la $t0, label; move $s0, $t0 => la $s0, label
                        // 只有当 laDst 之后不再被使用时才能合并
                        if (laDst.equals(moveSrc) && laDst.startsWith("$t") && !isRegUsedAfter(i + 2, laDst)) {
                            newLines.add(getIndent(line) + "la " + moveDst + ", " + laAddr);
                            i++; // 跳过 move
                            changed = true;
                            continue;
                        }
                    }
                }
            }
            
            // 处理 li 指令
            if (trimmed.startsWith("li ") && i + 1 < lines.size()) {
                String nextLine = lines.get(i + 1).trim();
                
                if (nextLine.startsWith("move ")) {
                    String[] liParts = trimmed.split("[\\s,]+");
                    String[] moveParts = nextLine.split("[\\s,]+");
                    
                    if (liParts.length >= 3 && moveParts.length >= 3) {
                        String liDst = liParts[1];
                        String liImm = liParts[2];
                        String moveDst = moveParts[1];
                        String moveSrc = moveParts[2];
                        
                        // li $t0, imm; move $s0, $t0 => li $s0, imm
                        // 但只有当 $t0 之后不再被使用时才能合并
                        if (liDst.equals(moveSrc) && liDst.startsWith("$t") && !isRegUsedAfter(i + 2, liDst)) {
                            newLines.add(getIndent(line) + "li " + moveDst + ", " + liImm);
                            i++; // 跳过 move
                            changed = true;
                            continue;
                        }
                    }
                }
            }
            
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }
    
    /**
     * 优化 move 链 - 保守版本
     * 只处理安全的情况：
     * 
     * 连续两个 move 到同一个目标（第一个被覆盖）
     * move $t0, $t1
     * move $t0, $t2
     * => move $t0, $t2  (删除第一个)
     */
    private boolean eliminateMoveChain() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // 检查连续的 move 指令是否可以合并
            if (trimmed.startsWith("move ") && i + 1 < lines.size()) {
                String nextLine = lines.get(i + 1).trim();
                
                if (nextLine.startsWith("move ")) {
                    String[] curr = trimmed.split("[\\s,]+");
                    String[] next = nextLine.split("[\\s,]+");
                    
                    if (curr.length >= 3 && next.length >= 3) {
                        String currDst = curr[1];
                        String nextDst = next[1];
                        
                        // 如果两个 move 的目标相同，删除第一个
                        // move $t0, $t1; move $t0, $t2 => move $t0, $t2
                        if (currDst.equals(nextDst)) {
                            // 跳过当前行
                            changed = true;
                            continue;
                        }
                    }
                }
            }
            
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }

    /**
     * 消除 move $t, $s 后紧跟使用 $t 作为源操作数的模式
     * 
     * move $t0, $s0
     * move $t1, $s1
     * mul $s2, $t0, $t1
     * =>
     * mul $s2, $s0, $s1
     * 
     * 条件：$t0, $t1 是临时寄存器，之后不再使用
     */
    private boolean eliminateMoveBeforeOp() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        // 追踪最近的 move: dst -> src
        Map<String, String> moveMap = new HashMap<>();
        // 追踪每个 move 指令的行索引，用于删除
        Map<String, Integer> moveLineIndex = new HashMap<>();
        // 追踪需要删除的行
        Set<Integer> toDelete = new HashSet<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // 遇到标签、分支、跳转、调用，清空追踪
            if (trimmed.endsWith(":") || trimmed.startsWith("j ") || 
                trimmed.startsWith("jr ") || trimmed.startsWith("jal ") ||
                trimmed.startsWith("beq ") || trimmed.startsWith("bne ") ||
                trimmed.startsWith("blt ") || trimmed.startsWith("ble ") ||
                trimmed.startsWith("bgt ") || trimmed.startsWith("bge ") ||
                trimmed.startsWith("syscall")) {
                moveMap.clear();
                moveLineIndex.clear();
                newLines.add(line);
                continue;
            }
            
            // 记录 move 指令
            if (trimmed.startsWith("move ")) {
                String[] parts = trimmed.split("[\\s,]+");
                if (parts.length >= 3) {
                    String dst = parts[1];
                    String src = parts[2];
                    
                    // 只追踪临时寄存器
                    if (dst.startsWith("$t") && !dst.equals(src)) {
                        // 如果 dst 之前有映射，先清除
                        moveMap.put(dst, src);
                        moveLineIndex.put(dst, i);
                    } else {
                        // 非临时寄存器作为目标，清除该寄存器的追踪
                        moveMap.remove(dst);
                        moveLineIndex.remove(dst);
                    }
                    
                    // 如果源寄存器被移动过，更新它
                    if (moveMap.containsKey(src)) {
                        moveMap.remove(src);
                        moveLineIndex.remove(src);
                    }
                }
                newLines.add(line);
                continue;
            }
            
            // 对于算术/逻辑指令，尝试替换
            if (isArithLogicInstr(trimmed) && !moveMap.isEmpty()) {
                String newLine = line;
                Set<String> usedMoves = new HashSet<>();
                
                for (Map.Entry<String, String> entry : moveMap.entrySet()) {
                    String tmpReg = entry.getKey();
                    String origReg = entry.getValue();
                    
                    // 检查指令是否使用 tmpReg 作为源操作数（非目标）
                    if (usesRegAsSource(trimmed, tmpReg)) {
                        // 替换
                        String replaced = replaceSourceReg(newLine, tmpReg, origReg);
                        if (replaced != null) {
                            newLine = replaced;
                            usedMoves.add(tmpReg);
                        }
                    }
                }
                
                // 如果发生了替换，标记相关的 move 指令为待删除
                for (String tmpReg : usedMoves) {
                    Integer moveIdx = moveLineIndex.get(tmpReg);
                    if (moveIdx != null) {
                        toDelete.add(moveIdx);
                        changed = true;
                    }
                    // 清除追踪
                    moveMap.remove(tmpReg);
                    moveLineIndex.remove(tmpReg);
                }
                
                newLines.add(newLine);
                
                // 检查指令是否定义了某个被追踪的寄存器
                String defReg = getDefReg(trimmed);
                if (defReg != null) {
                    moveMap.remove(defReg);
                    moveLineIndex.remove(defReg);
                }
                continue;
            }
            
            // 其他指令可能会破坏追踪
            if (trimmed.startsWith("sw ") || trimmed.startsWith("lw ") || 
                trimmed.startsWith("lb ") || trimmed.startsWith("sb ") ||
                trimmed.startsWith("la ") || trimmed.startsWith("li ")) {
                // 这些指令可能修改寄存器
                String defReg = getDefReg(trimmed);
                if (defReg != null) {
                    moveMap.remove(defReg);
                    moveLineIndex.remove(defReg);
                }
            }
            
            newLines.add(line);
        }
        
        // 现在从 newLines 中删除被标记的 move 指令
        if (!toDelete.isEmpty()) {
            List<String> finalLines = new ArrayList<>();
            // 需要重新映射索引，因为 newLines 的索引可能与 lines 不同
            // 简化处理：重新扫描并删除匹配的 move
            Set<String> deletedMoves = new HashSet<>();
            for (int idx : toDelete) {
                if (idx < lines.size()) {
                    deletedMoves.add(lines.get(idx).trim());
                }
            }
            
            for (String ln : newLines) {
                if (deletedMoves.contains(ln.trim())) {
                    deletedMoves.remove(ln.trim()); // 只删除一次
                    continue;
                }
                finalLines.add(ln);
            }
            this.lines = finalLines;
        } else {
            this.lines = newLines;
        }
        
        return changed;
    }
    
    /**
     * 检查指令是否是算术/逻辑指令
     */
    private boolean isArithLogicInstr(String instr) {
        return instr.startsWith("add") || instr.startsWith("sub") ||
               instr.startsWith("mul") || instr.startsWith("div") ||
               instr.startsWith("and ") || instr.startsWith("or ") ||
               instr.startsWith("xor ") || instr.startsWith("nor ") ||
               instr.startsWith("sll ") || instr.startsWith("srl ") ||
               instr.startsWith("sra ") || instr.startsWith("slt") ||
               instr.startsWith("seq ") || instr.startsWith("sne ") ||
               instr.startsWith("sle ") || instr.startsWith("sge ") ||
               instr.startsWith("sgt ");
    }
    
    /**
     * 检查指令是否使用 reg 作为源操作数（不是目标）
     */
    private boolean usesRegAsSource(String instr, String reg) {
        String[] parts = instr.split("[\\s,]+");
        if (parts.length < 3) return false;
        
        // 第一个是指令，第二个通常是目标，后面是源
        for (int i = 2; i < parts.length; i++) {
            if (parts[i].equals(reg)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 替换指令中的源操作数寄存器
     */
    private String replaceSourceReg(String line, String oldReg, String newReg) {
        String indent = getIndent(line);
        String trimmed = line.trim();
        String[] parts = trimmed.split("[\\s,]+");
        
        if (parts.length < 3) return null;
        
        boolean replaced = false;
        StringBuilder sb = new StringBuilder(indent);
        sb.append(parts[0]).append(" ").append(parts[1]); // op dst
        
        for (int i = 2; i < parts.length; i++) {
            sb.append(", ");
            if (parts[i].equals(oldReg)) {
                sb.append(newReg);
                replaced = true;
            } else {
                sb.append(parts[i]);
            }
        }
        
        return replaced ? sb.toString() : null;
    }

    /**
     * 分支优化：合并比较和跳转指令
     * slt $t0, $t1, $t2
     * bne $t0, $zero, label
     * -> blt $t1, $t2, label
     */
    private boolean optimizeBranches() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            if (i < lines.size() - 1) {
                String nextLine = lines.get(i+1);
                String nextTrimmed = nextLine.trim();
                
                // 模式匹配: 比较指令 + 分支指令
                if (isCompareInstr(trimmed) && isBranchInstr(nextTrimmed)) {
                    String[] cmpParts = parseInstr(trimmed);
                    String[] brParts = parseInstr(nextTrimmed);
                    
                    if (cmpParts != null && brParts != null) {
                        String cmpOp = cmpParts[0];
                        String cmpDest = cmpParts[1];
                        String cmpSrc1 = cmpParts[2];
                        String cmpSrc2 = cmpParts[3];
                        
                        String brOp = brParts[0];
                        String brSrc1 = brParts[1];
                        String brSrc2 = brParts[2]; // usually $zero
                        String label = brParts[3];
                        
                        // 确认分支指令使用的是比较结果
                        // bne $t0, $zero, label
                        if (brSrc1.equals(cmpDest) && brSrc2.equals("$zero")) {
                            String newOp = null;
                            
                            // 逻辑推导表
                            if (brOp.equals("bne")) { // if (dest != 0) -> 条件成立
                                if (cmpOp.equals("slt")) newOp = "blt";
                                else if (cmpOp.equals("sle")) newOp = "ble";
                                else if (cmpOp.equals("sgt")) newOp = "bgt";
                                else if (cmpOp.equals("sge")) newOp = "bge";
                                else if (cmpOp.equals("seq")) newOp = "beq";
                                else if (cmpOp.equals("sne")) newOp = "bne";
                            } else if (brOp.equals("beq")) { // if (dest == 0) -> 条件不成立
                                if (cmpOp.equals("slt")) newOp = "bge"; // ! <  -> >=
                                else if (cmpOp.equals("sle")) newOp = "bgt"; // ! <= -> >
                                else if (cmpOp.equals("sgt")) newOp = "ble"; // ! >  -> <=
                                else if (cmpOp.equals("sge")) newOp = "blt"; // ! >= -> <
                                else if (cmpOp.equals("seq")) newOp = "bne"; // ! == -> !=
                                else if (cmpOp.equals("sne")) newOp = "beq"; // ! != -> ==
                            }
                            
                            if (newOp != null) {
                                // 生成新指令: blt $t1, $t2, label
                                newLines.add("  " + newOp + " " + cmpSrc1 + ", " + cmpSrc2 + ", " + label);
                                i++; // 跳过下一行
                                changed = true;
                                continue;
                            }
                        }
                    }
                }
            }
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }

    // 现有的优化保持不变
    private boolean eliminateRedundantLoadStore() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (i == lines.size() - 1) { newLines.add(line); break; }

            String nextLine = lines.get(i + 1);
            String nextTrimmed = nextLine.trim();
            
            String[] curr = parseMemInstr(trimmed);
            String[] next = parseMemInstr(nextTrimmed);

            if (curr != null && next != null) {
                String op1 = curr[0]; String reg1 = curr[1]; String addr1 = curr[2];
                String op2 = next[0]; String reg2 = next[1]; String addr2 = next[2];

                if (addr1.equals(addr2)) {
                    // Store-Store: sw $t0, off -> sw $t1, off (第一个 sw 被覆盖，删除)
                    if (op1.equals("sw") && op2.equals("sw")) {
                        // 跳过第一条 sw，只保留第二条
                        changed = true;
                        continue;  // 不添加当前行
                    }
                    // Store-Load: sw $t0, off -> lw $t1, off
                    if (op1.equals("sw") && op2.equals("lw")) {
                        newLines.add(line); 
                        if (!reg1.equals(reg2)) {
                            newLines.add(getIndent(nextLine) + "move " + reg2 + ", " + reg1);
                        }
                        i++; changed = true; continue;
                    }
                    // Load-Load: lw $t0, off -> lw $t1, off
                    if (op1.equals("lw") && op2.equals("lw")) {
                        newLines.add(line);
                        if (!reg1.equals(reg2)) {
                            newLines.add(getIndent(nextLine) + "move " + reg2 + ", " + reg1);
                        }
                        i++; changed = true; continue;
                    }
                }
            }
            newLines.add(line);
        }
        this.lines = newLines;
        return changed;
    }

    /**
     * 扩展的 Store-Load 转发优化（在基本块内，不仅限于相邻指令）
     * 
     * 模式: sw $t0, addr; ... (没有修改 $t0 或 addr); lw $t1, addr
     * 优化: 如果 sw 到 lw 之间 $t0 没有被修改，addr 没有被写入，
     *       则可以用 move $t1, $t0 替换 lw $t1, addr
     */
    private boolean storeLoadForwarding() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        // 追踪最近的 store: 地址 -> (行号, 寄存器)
        Map<String, int[]> lastStore = new HashMap<>(); // addr -> [lineIndex, regValue]
        Map<String, String> storeReg = new HashMap<>(); // addr -> reg
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // 标签/跳转/调用会打断追踪
            if (trimmed.endsWith(":") || trimmed.startsWith("j ") || trimmed.startsWith("jal ") ||
                trimmed.startsWith("jr ") || trimmed.startsWith("beq ") || trimmed.startsWith("bne ") ||
                trimmed.startsWith("blt ") || trimmed.startsWith("ble ") ||
                trimmed.startsWith("bgt ") || trimmed.startsWith("bge ") ||
                trimmed.startsWith("syscall")) {
                lastStore.clear();
                storeReg.clear();
                newLines.add(line);
                continue;
            }
            
            String[] memInfo = parseMemInstr(trimmed);
            
            if (memInfo != null) {
                String op = memInfo[0];
                String reg = memInfo[1];
                String addr = memInfo[2];
                
                if (op.equals("sw")) {
                    // 记录这个 store
                    lastStore.put(addr, new int[]{i});
                    storeReg.put(addr, reg);
                    newLines.add(line);
                } else if (op.equals("lw")) {
                    // 检查是否有对应的 store
                    if (lastStore.containsKey(addr)) {
                        String storedReg = storeReg.get(addr);
                        // 如果值相同，可以删除这个 load
                        if (reg.equals(storedReg)) {
                            // lw $t0, addr 在 sw $t0, addr 之后，直接删除
                            changed = true;
                            continue;
                        } else {
                            // 用 move 替换 lw
                            newLines.add(getIndent(line) + "move " + reg + ", " + storedReg);
                            changed = true;
                            continue;
                        }
                    } else {
                        newLines.add(line);
                    }
                    // load 之后，这个地址的值已经在寄存器中
                    lastStore.put(addr, new int[]{i});
                    storeReg.put(addr, reg);
                } else {
                    newLines.add(line);
                }
            } else {
                // 检查这个指令是否修改了某个追踪中的寄存器
                String defReg = getDefReg(trimmed);
                if (defReg != null) {
                    // 移除所有使用这个寄存器的追踪
                    storeReg.entrySet().removeIf(e -> e.getValue().equals(defReg));
                    lastStore.keySet().removeIf(addr -> !storeReg.containsKey(addr));
                }
                newLines.add(line);
            }
        }
        
        this.lines = newLines;
        return changed;
    }

    /**
     * 分支跳转优化：
     * beq $t0, $zero, L1
     * j L2
     * L1:
     * -> bne $t0, $zero, L2
     *    L1:
     * 
     * 同样适用于 bne -> beq 的转换
     */
    private boolean optimizeBranchJump() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // 检查是否是条件分支指令
            if ((trimmed.startsWith("beq ") || trimmed.startsWith("bne ") ||
                 trimmed.startsWith("blt ") || trimmed.startsWith("ble ") ||
                 trimmed.startsWith("bgt ") || trimmed.startsWith("bge ")) &&
                i + 2 < lines.size()) {
                
                String nextLine = lines.get(i + 1).trim();
                String labelLine = lines.get(i + 2).trim();
                
                // 下一条是无条件跳转?
                if (nextLine.startsWith("j ")) {
                    String jTarget = nextLine.substring(2).trim();
                    
                    // 再下一行是分支的目标标签?
                    String[] brParts = trimmed.split("[\\s,]+");
                    if (brParts.length >= 4) {
                        String brLabel = brParts[brParts.length - 1];
                        
                        if (labelLine.equals(brLabel + ":")) {
                            // 匹配成功！反转分支条件
                            String newBrOp = invertBranch(brParts[0]);
                            if (newBrOp != null) {
                                // 构建新的分支指令
                                StringBuilder sb = new StringBuilder(getIndent(line));
                                sb.append(newBrOp).append(" ");
                                for (int j = 1; j < brParts.length - 1; j++) {
                                    sb.append(brParts[j]);
                                    if (j < brParts.length - 2) sb.append(", ");
                                }
                                sb.append(", ").append(jTarget);
                                newLines.add(sb.toString());
                                // 跳过 j 指令，保留标签行
                                i += 1; // 跳过 j，下次循环会处理标签
                                changed = true;
                                continue;
                            }
                        }
                    }
                }
            }
            
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }

    /**
     * 反转分支条件
     */
    private String invertBranch(String op) {
        switch (op) {
            case "beq": return "bne";
            case "bne": return "beq";
            case "blt": return "bge";
            case "bge": return "blt";
            case "ble": return "bgt";
            case "bgt": return "ble";
            default: return null;
        }
    }
    
    private boolean eliminateRedundantJumps() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith("j ")) {
                String label = line.trim().substring(2).trim();
                // 查找下一个非空、非注释行
                int nextIdx = -1;
                for (int j = i + 1; j < lines.size() && j <= i + 3; j++) {
                    String t = lines.get(j).trim();
                    if (!t.isEmpty() && !t.startsWith("#")) {
                        nextIdx = j;
                        break;
                    }
                }
                if (nextIdx != -1 && lines.get(nextIdx).trim().equals(label + ":")) {
                    // 跳过这个 j 指令，但添加中间的空行/注释
                    for (int j = i + 1; j < nextIdx; j++) {
                        newLines.add(lines.get(j));
                    }
                    changed = true;
                    i = nextIdx - 1; // 下一轮循环会处理标签
                    continue;
                }
            }
            newLines.add(line);
        }
        this.lines = newLines;
        return changed;
    }

    /**
     * 消除 fall-through 的无条件跳转
     * 例如: 在条件分支后，如果 false 分支就跳到下一个标签，则该跳转是冗余的
     */
    private boolean eliminateFallthroughJumps() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // 检查是否是无条件跳转
            if (trimmed.startsWith("j ") && !trimmed.startsWith("jal ") && !trimmed.startsWith("jr ")) {
                String target = trimmed.substring(2).trim();
                
                // 查找下一个实际指令或标签
                int nextIdx = i + 1;
                while (nextIdx < lines.size()) {
                    String nextLine = lines.get(nextIdx).trim();
                    if (!nextLine.isEmpty() && !nextLine.startsWith("#")) {
                        break;
                    }
                    nextIdx++;
                }
                
                // 如果下一行就是目标标签，则删除此跳转
                if (nextIdx < lines.size() && lines.get(nextIdx).trim().equals(target + ":")) {
                    changed = true;
                    continue; // 不添加这个j指令
                }
            }
            
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }

    private boolean eliminateRedundantMoves() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().startsWith("move ")) {
                String[] p = line.trim().substring(5).split(",");
                if (p.length == 2 && p[0].trim().equals(p[1].trim())) {
                    changed = true; continue;
                }
            }
            newLines.add(line);
        }
        this.lines = newLines;
        return changed;
    }

    // --- Helpers ---

    private boolean isCompareInstr(String line) {
        return line.startsWith("slt ") || line.startsWith("sle ") || 
               line.startsWith("sgt ") || line.startsWith("sge ") ||
               line.startsWith("seq ") || line.startsWith("sne ");
    }
    
    private boolean isBranchInstr(String line) {
        return line.startsWith("beq ") || line.startsWith("bne ");
    }

    // 解析通用指令: op arg1, arg2, arg3
    private String[] parseInstr(String line) {
        try {
            String[] parts = line.split("[\\s,]+");
            if (parts.length < 4) return null;
            return new String[]{parts[0], parts[1], parts[2], parts[3]};
        } catch (Exception e) { return null; }
    }

    private String[] parseMemInstr(String line) {
        if (line.startsWith("#")) return null;
        try {
            String op = line.startsWith("sw ") ? "sw" : (line.startsWith("lw ") ? "lw" : null);
            if (op == null) return null;
            String content = line.substring(2).trim();
            int commaIdx = content.indexOf(',');
            if (commaIdx == -1) return null;
            String reg = content.substring(0, commaIdx).trim();
            String addr = content.substring(commaIdx + 1).trim();
            return new String[]{op, reg, addr};
        } catch (Exception e) { return null; }
    }

    private String getIndent(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        return line.substring(0, i);
    }
    
    /**
     * Move 传播和死代码消除
     * 在基本块内：
     * 1. 如果 move $t, $s 后紧跟一条指令使用 $t，可以直接用 $s 替换，删除 move
     * 2. 如果一个临时寄存器被定义但之后立即被重新定义，删除第一个定义
     * 
     * 这是一个保守的实现，只处理简单模式
     */
    private boolean propagateMoveAndEliminate() {
        boolean changed = false;
        List<String> newLines = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            
            // 跳过注释和标签
            if (trimmed.startsWith("#") || trimmed.endsWith(":") || trimmed.isEmpty()) {
                newLines.add(line);
                continue;
            }
            
            // 模式1: move $t, $s 后紧跟使用 $t 的指令
            // 只有当 moveDst 在之后不再被使用（直到被重新定义）时，才能删除 move
            if (trimmed.startsWith("move ") && i + 1 < lines.size()) {
                String[] moveParts = trimmed.split("[\\s,]+");
                if (moveParts.length >= 3) {
                    String moveDst = moveParts[1];
                    String moveSrc = moveParts[2];
                    
                    // 只处理临时寄存器作为目标的情况
                    if (moveDst.startsWith("$t") && !moveSrc.equals(moveDst)) {
                        String nextLine = lines.get(i + 1);
                        String nextTrimmed = nextLine.trim();
                        
                        // 检查下一条指令是否只读 moveDst，不写它
                        if (canPropagateMoveToNext(moveDst, moveSrc, nextTrimmed)) {
                            // 额外检查：确保 moveDst 在之后不再被使用（直到重新定义）
                            // 否则删除 move 会导致后续指令使用未定义的值
                            if (!isRegUsedAfter(i + 2, moveDst)) {
                                // 替换下一条指令中的 moveDst 为 moveSrc
                                String replacedNext = replaceRegInInstr(nextLine, moveDst, moveSrc);
                                if (replacedNext != null) {
                                    // 跳过当前 move，添加替换后的下一条指令
                                    newLines.add(replacedNext);
                                    i++; // 跳过下一行
                                    changed = true;
                                    continue;
                                }
                            }
                        }
                    }
                }
            }
            
            // 模式1.5: la $reg, addr + (注释) + move $a0, $reg -> la $a0, addr + (注释)
            // 这个模式可以节省一条 move 指令
            if (trimmed.startsWith("la ")) {
                String[] laParts = trimmed.split("[\\s,]+");
                if (laParts.length >= 3) {
                    String laReg = laParts[1];
                    String laAddr = laParts[2];
                    
                    // 查找接下来的非注释指令
                    int nextCodeIdx = -1;
                    for (int j = i + 1; j < lines.size() && j <= i + 3; j++) {
                        String t = lines.get(j).trim();
                        if (!t.startsWith("#") && !t.isEmpty()) {
                            nextCodeIdx = j;
                            break;
                        }
                    }
                    
                    if (nextCodeIdx != -1) {
                        String nextCode = lines.get(nextCodeIdx).trim();
                        // 检查是否是 move $a0, $laReg
                        String escapedReg = java.util.regex.Pattern.quote(laReg);
                        if (nextCode.matches("move\\s+\\$a0\\s*,\\s*" + escapedReg + "\\s*")) {
                            // 将 la 的目标寄存器改为 $a0
                            String indent = line.substring(0, line.indexOf(trimmed));
                            newLines.add(indent + "la $a0, " + laAddr);
                            // 添加中间的注释行
                            for (int j = i + 1; j < nextCodeIdx; j++) {
                                newLines.add(lines.get(j));
                            }
                            // 跳过 la 和 move
                            i = nextCodeIdx;
                            changed = true;
                            continue;
                        }
                    }
                }
            }
            
            // 模式2: 检查是否是死定义（定义后立即被重新定义）
            if (i + 1 < lines.size()) {
                String defReg = getDefReg(trimmed);
                if (defReg != null && defReg.startsWith("$t")) {
                    String nextLine = lines.get(i + 1).trim();
                    String nextDefReg = getDefReg(nextLine);
                    
                    // 如果下一条指令定义同一个寄存器，且当前指令没有副作用
                    if (defReg.equals(nextDefReg) && !hasSideEffect(trimmed) && !usesReg(nextLine, defReg)) {
                        // 当前定义是死的，跳过
                        changed = true;
                        continue;
                    }
                }
            }
            
            newLines.add(line);
        }
        
        this.lines = newLines;
        return changed;
    }
    
    /**
     * 检查是否可以将 move 传播到下一条指令
     */
    private boolean canPropagateMoveToNext(String moveDst, String moveSrc, String nextInstr) {
        // 不处理标签、注释、分支、跳转、调用、存储
        if (nextInstr.isEmpty() || nextInstr.startsWith("#") || nextInstr.endsWith(":")) {
            return false;
        }
        if (nextInstr.startsWith("j ") || nextInstr.startsWith("jal ") || 
            nextInstr.startsWith("jr ") || nextInstr.startsWith("beq ") ||
            nextInstr.startsWith("bne ") || nextInstr.startsWith("blt ") ||
            nextInstr.startsWith("ble ") || nextInstr.startsWith("bgt ") ||
            nextInstr.startsWith("bge ") || nextInstr.startsWith("sw ") ||
            nextInstr.startsWith("syscall")) {
            return false;
        }
        
        // 下一条指令必须使用 moveDst
        if (!usesReg(nextInstr, moveDst)) {
            return false;
        }
        
        // 下一条指令不能定义 moveDst（否则 move 本身就是死的，由其他优化处理）
        String nextDef = getDefReg(nextInstr);
        if (moveDst.equals(nextDef)) {
            return false;
        }
        
        // 下一条指令不能定义 moveSrc（否则替换后语义改变）
        if (moveSrc.equals(nextDef)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 替换指令中的寄存器
     */
    private String replaceRegInInstr(String line, String oldReg, String newReg) {
        String indent = getIndent(line);
        String trimmed = line.trim();
        
        // 使用 lookahead/lookbehind 替换，确保只替换完整的寄存器名
        // \b 不能匹配 $ 前后的边界，所以使用 (?<![a-zA-Z0-9]) 和 (?![a-zA-Z0-9])
        String escapedOld = oldReg.replace("$", "\\$");
        // 替换字符串中的 $ 也需要转义（Java replaceAll 的特殊字符）
        String escapedNew = java.util.regex.Matcher.quoteReplacement(newReg);
        String result = trimmed.replaceAll("(?<![a-zA-Z0-9])" + escapedOld + "(?![a-zA-Z0-9])", escapedNew);
        
        if (result.equals(trimmed)) {
            return null; // 没有替换
        }
        return indent + result;
    }
    
    /**
     * 检查指令是否使用某个寄存器
     */
    private boolean usesReg(String instr, String reg) {
        String[] parts = instr.split("[\\s,()]+");
        for (String part : parts) {
            if (part.equals(reg)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查指令是否有副作用（不能删除）
     */
    private boolean hasSideEffect(String instr) {
        return instr.startsWith("sw ") || instr.startsWith("jal ") || 
               instr.startsWith("j ") || instr.startsWith("jr ") ||
               instr.startsWith("beq ") || instr.startsWith("bne ") ||
               instr.startsWith("blt ") || instr.startsWith("ble ") ||
               instr.startsWith("bgt ") || instr.startsWith("bge ") ||
               instr.startsWith("syscall");
    }
    
    /**
     * 检查从 startIdx 开始，寄存器 reg 是否在被重新定义之前被使用
     * 用于确定能否安全地删除一个 move 指令
     * @return true 如果 reg 在被重新定义之前被使用（或遇到基本块边界）
     */
    private boolean isRegUsedAfter(int startIdx, String reg) {
        for (int i = startIdx; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            
            // 跳过空行和注释
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            
            // 遇到标签或分支/跳转，保守地认为可能被使用
            if (trimmed.endsWith(":") || trimmed.startsWith("j ") || 
                trimmed.startsWith("jr ") || trimmed.startsWith("jal ") ||
                trimmed.startsWith("beq ") || trimmed.startsWith("bne ") ||
                trimmed.startsWith("blt ") || trimmed.startsWith("ble ") ||
                trimmed.startsWith("bgt ") || trimmed.startsWith("bge ")) {
                return true; // 保守返回 true
            }
            
            // 检查这条指令是否使用 reg
            if (usesReg(trimmed, reg)) {
                // 检查是否同时定义 reg（如 addu $t0, $t0, $t1）
                String defReg = getDefReg(trimmed);
                if (reg.equals(defReg)) {
                    // 先用后定义，仍然算使用
                    return true;
                }
                // 纯粹的使用
                return true;
            }
            
            // 检查这条指令是否重新定义 reg
            String defReg = getDefReg(trimmed);
            if (reg.equals(defReg)) {
                // reg 被重新定义了，之前没有使用，安全
                return false;
            }
        }
        // 到达函数末尾，保守地认为可能被使用
        return true;
    }
}
