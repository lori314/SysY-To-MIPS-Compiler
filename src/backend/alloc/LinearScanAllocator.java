package backend.alloc;

import midend.ir.values.Argument;
import midend.ir.values.Function;
import midend.ir.values.Value;
import midend.ir.values.instructions.AllocaInst;
import midend.ir.values.instructions.Instruction;
import midend.optim.LivenessAnalysis;

import java.util.*;

/**
 * 线性扫描寄存器分配器 (Linear Scan Register Allocator)
 * 
 * 使用活跃区间分析来分配寄存器，比简单的使用计数更高效。
 * 可以利用寄存器的活跃期结束后重新分配给其他变量。
 */
public class LinearScanAllocator {
    
    // 可用的被调用者保存寄存器 ($s0-$s7)
    private static final String[] CALLEE_SAVED = {"$s0", "$s1", "$s2", "$s3", "$s4", "$s5", "$s6", "$s7"};
    
    // 可用的调用者保存寄存器 ($t0-$t6)，$t7-$t9 保留给代码生成
    private static final String[] CALLER_SAVED = {"$t0", "$t1", "$t2", "$t3", "$t4", "$t5", "$t6"};
    
    // 分配结果
    private final Map<Value, String> registerMap = new HashMap<>();
    private final Set<String> usedCalleeSaved = new HashSet<>();
    private final Set<String> usedCallerSaved = new HashSet<>();
    
    // 活跃区间
    private List<IntervalInfo> intervals;
    
    // 活跃的区间（按结束点排序）
    private final PriorityQueue<IntervalInfo> active = new PriorityQueue<>(
        Comparator.comparingInt(a -> a.interval.end)
    );
    
    // 可用寄存器池
    private final LinkedList<String> freeCalleeSaved = new LinkedList<>();
    private final LinkedList<String> freeCallerSaved = new LinkedList<>();
    
    /**
     * 对函数进行寄存器分配
     */
    public void allocate(Function func, Map<Value, LivenessAnalysis.LiveInterval> liveIntervals) {
        if (liveIntervals.isEmpty()) return;
        
        // 初始化寄存器池
        freeCalleeSaved.clear();
        freeCallerSaved.clear();
        Collections.addAll(freeCalleeSaved, CALLEE_SAVED);
        Collections.addAll(freeCallerSaved, CALLER_SAVED);
        
        active.clear();
        registerMap.clear();
        usedCalleeSaved.clear();
        usedCallerSaved.clear();
        
        // 构建区间列表并排序
        intervals = new ArrayList<>();
        for (Map.Entry<Value, LivenessAnalysis.LiveInterval> entry : liveIntervals.entrySet()) {
            Value v = entry.getKey();
            LivenessAnalysis.LiveInterval interval = entry.getValue();
            
            // 跳过 alloca 指令（它们需要栈空间存储地址）
            if (v instanceof AllocaInst) continue;
            
            IntervalInfo info = new IntervalInfo();
            info.value = v;
            info.interval = interval;
            info.isArgument = v instanceof Argument;
            info.spillCost = computeSpillCost(v, interval);
            intervals.add(info);
        }
        
        // 按起始点排序
        intervals.sort(Comparator.comparingInt(a -> a.interval.start));
        
        // 线性扫描
        for (IntervalInfo current : intervals) {
            // 释放已结束的区间
            expireOldIntervals(current.interval.start);
            
            // 尝试分配寄存器
            String reg = tryAllocateRegister(current);
            
            if (reg != null) {
                registerMap.put(current.value, reg);
                current.assignedReg = reg;
                active.add(current);
            } else {
                // 需要溢出
                spillAtInterval(current);
            }
        }
    }
    
    private void expireOldIntervals(int position) {
        while (!active.isEmpty() && active.peek().interval.end <= position) {
            IntervalInfo expired = active.poll();
            freeRegister(expired.assignedReg);
        }
    }
    
    private String tryAllocateRegister(IntervalInfo info) {
        // 优先使用 callee-saved 寄存器（跨函数调用保持不变）
        if (!freeCalleeSaved.isEmpty()) {
            String reg = freeCalleeSaved.removeFirst();
            usedCalleeSaved.add(reg);
            return reg;
        }
        
        // 如果变量不跨越调用，可以使用 caller-saved 寄存器
        if (!info.crossesCall && !freeCallerSaved.isEmpty()) {
            String reg = freeCallerSaved.removeFirst();
            usedCallerSaved.add(reg);
            return reg;
        }
        
        // 没有可用寄存器
        return null;
    }
    
    private void freeRegister(String reg) {
        if (reg == null) return;
        
        if (reg.startsWith("$s")) {
            if (!freeCalleeSaved.contains(reg)) {
                freeCalleeSaved.addFirst(reg);
            }
        } else if (reg.startsWith("$t")) {
            if (!freeCallerSaved.contains(reg)) {
                freeCallerSaved.addFirst(reg);
            }
        }
    }
    
    private void spillAtInterval(IntervalInfo current) {
        // 选择溢出代价最高的活跃区间（或当前区间）
        // 如果当前区间的结束点最晚，溢出它
        // 否则溢出活跃区间中结束点最晚的
        
        IntervalInfo toSpill = null;
        
        // 在活跃区间中找到结束点最晚且溢出代价低的
        for (IntervalInfo a : active) {
            if (a.interval.end > current.interval.end) {
                if (toSpill == null || a.spillCost < toSpill.spillCost) {
                    toSpill = a;
                }
            }
        }
        
        if (toSpill != null && toSpill.spillCost < current.spillCost) {
            // 溢出活跃区间，把它的寄存器给当前区间
            String reg = toSpill.assignedReg;
            active.remove(toSpill);
            registerMap.remove(toSpill.value);
            toSpill.assignedReg = null;
            
            registerMap.put(current.value, reg);
            current.assignedReg = reg;
            active.add(current);
        }
        // 否则溢出当前区间（不分配寄存器）
    }
    
    private int computeSpillCost(Value v, LivenessAnalysis.LiveInterval interval) {
        // 简单的溢出代价：区间长度（更短的区间溢出代价更低）
        // 可以增强：考虑使用次数、是否在循环中等
        int cost = interval.length();
        
        // 参数的溢出代价较高（需要额外的 load）
        if (v instanceof Argument) {
            cost += 100;
        }
        
        return cost;
    }
    
    /**
     * 获取分配结果
     */
    public Map<Value, String> getRegisterMap() {
        return registerMap;
    }
    
    /**
     * 获取使用的 callee-saved 寄存器（需要在函数入口保存）
     */
    public Set<String> getUsedCalleeSaved() {
        return usedCalleeSaved;
    }
    
    /**
     * 获取使用的 caller-saved 寄存器
     */
    public Set<String> getUsedCallerSaved() {
        return usedCallerSaved;
    }
    
    private static class IntervalInfo {
        Value value;
        LivenessAnalysis.LiveInterval interval;
        String assignedReg;
        boolean isArgument;
        boolean crossesCall;
        int spillCost;
    }
}
