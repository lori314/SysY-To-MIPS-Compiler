package backend.target;

import java.util.ArrayList;
import java.util.List;

/**
 * 乘法常数优化器 - 简化版
 * 
 * 将乘法常数转换为移位和加减操作的组合。
 * 只处理简单情况：2的幂、2的幂±1、两个2的幂之和/差
 */
public class MulOptimizer {
    
    // mul 指令成本 (5 cycles in MARS)
    private static final int MUL_COST = 5;
    
    /**
     * 生成乘法优化的 MIPS 代码
     * @param srcReg 源寄存器（被乘数）
     * @param dstReg 目标寄存器
     * @param multiplier 乘法常数
     * @param tempRegs 可用的临时寄存器（至少 1 个）
     * @return MIPS 代码行列表，如果无法优化则返回 null
     */
    public static List<String> generateOptimizedMul(String srcReg, String dstReg, 
                                                     int multiplier, String[] tempRegs) {
        List<String> code = new ArrayList<>();
        String temp = tempRegs[0];
        String temp2 = tempRegs.length > 1 ? tempRegs[1] : temp;
        
        // 特殊情况
        if (multiplier == 0) {
            code.add("  move " + dstReg + ", $zero");
            return code;
        }
        if (multiplier == 1) {
            if (!srcReg.equals(dstReg)) {
                code.add("  move " + dstReg + ", " + srcReg);
            }
            return code;
        }
        if (multiplier == -1) {
            code.add("  subu " + dstReg + ", $zero, " + srcReg);
            return code;
        }
        
        // 处理负数
        boolean negative = multiplier < 0;
        int m = Math.abs(multiplier);
        
        // 检查是否是 2 的幂
        if (isPowerOf2(m)) {
            int k = log2(m);
            code.add("  sll " + dstReg + ", " + srcReg + ", " + k);
            if (negative) {
                code.add("  subu " + dstReg + ", $zero, " + dstReg);
            }
            return code; // 1-2 条指令，成本 1-2 < 5
        }
        
        // 检查是否是 2^k + 1 (如 3, 5, 9, 17, 33, 65, ...)
        if (isPowerOf2(m - 1)) {
            int k = log2(m - 1);
            // x * (2^k + 1) = (x << k) + x
            code.add("  sll " + temp + ", " + srcReg + ", " + k);
            code.add("  addu " + dstReg + ", " + temp + ", " + srcReg);
            if (negative) {
                code.add("  subu " + dstReg + ", $zero, " + dstReg);
            }
            return code; // 2-3 条指令，成本 2-3 < 5
        }
        
        // 检查是否是 2^k - 1 (如 3, 7, 15, 31, 63, ...)
        if (isPowerOf2(m + 1)) {
            int k = log2(m + 1);
            // x * (2^k - 1) = (x << k) - x
            code.add("  sll " + temp + ", " + srcReg + ", " + k);
            code.add("  subu " + dstReg + ", " + temp + ", " + srcReg);
            if (negative) {
                code.add("  subu " + dstReg + ", $zero, " + dstReg);
            }
            return code; // 2-3 条指令，成本 2-3 < 5
        }
        
        // 扩展：检查更多模式
        // x * 6 = x * (4 + 2) = (x << 2) + (x << 1)
        // x * 7 = x * (8 - 1) = (x << 3) - x
        // x * 10 = x * (8 + 2) = (x << 3) + (x << 1)
        // x * 11 = x * (8 + 2 + 1) = (x << 3) + (x << 1) + x
        // x * 12 = x * (8 + 4) = (x << 3) + (x << 2)
        // x * 13 = x * (16 - 3) = (x << 4) - (x + (x << 1))
        // x * 15 = x * (16 - 1) = (x << 4) - x
        // x * 18 = x * (16 + 2) = (x << 4) + (x << 1)
        // x * 20 = x * (16 + 4) = (x << 4) + (x << 2)
        // x * 24 = x * (16 + 8) = (x << 4) + (x << 3)
        // x * 25 = x * (32 - 8 + 1) = (x << 5) - (x << 3) + x
        
        // 通用策略：尝试分解为最多3个移位+加减操作
        List<String> bestCode = tryDecompose(srcReg, dstReg, m, temp, temp2, negative);
        if (bestCode != null && bestCode.size() < MUL_COST) {
            return bestCode;
        }
        
        // 检查是否是 2^k + 2^j (如 6=4+2, 10=8+2, 12=8+4, ...)
        for (int k = 1; k < 31; k++) {
            int pk = 1 << k;
            if (pk > m) break;
            int remainder = m - pk;
            if (remainder > 0 && isPowerOf2(remainder)) {
                int j = log2(remainder);
                // x * (2^k + 2^j) = (x << k) + (x << j)
                code.add("  sll " + dstReg + ", " + srcReg + ", " + k);
                code.add("  sll " + temp + ", " + srcReg + ", " + j);
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                if (negative) {
                    code.add("  subu " + dstReg + ", $zero, " + dstReg);
                }
                return code; // 3-4 条指令，成本 3-4 < 5
            }
        }
        
        // 检查是否是 2^k - 2^j (如 6=8-2, 14=16-2, ...)
        for (int k = 2; k < 31; k++) {
            int pk = 1 << k;
            if (pk <= m) continue;
            int diff = pk - m;
            if (diff > 0 && isPowerOf2(diff)) {
                int j = log2(diff);
                // x * (2^k - 2^j) = (x << k) - (x << j)
                code.add("  sll " + dstReg + ", " + srcReg + ", " + k);
                code.add("  sll " + temp + ", " + srcReg + ", " + j);
                code.add("  subu " + dstReg + ", " + dstReg + ", " + temp);
                if (negative) {
                    code.add("  subu " + dstReg + ", $zero, " + dstReg);
                }
                return code; // 3-4 条指令，成本 3-4 < 5
            }
        }
        
        // 无法优化，使用标准乘法
        return null;
    }
    
    /**
     * 尝试将乘数分解为移位+加减的组合
     * 使用贪心算法找到最优分解
     * 约束：(移位次数 + 加减次数) < 5 时才替换
     */
    private static List<String> tryDecompose(String srcReg, String dstReg, int m, 
                                             String temp, String temp2, boolean negative) {
        // 首先检查常见模式的手工优化
        List<String> code = new ArrayList<>();
        
        switch (m) {
            case 3:  // (x << 1) + x
                code.add("  sll " + temp + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 5:  // (x << 2) + x
                code.add("  sll " + temp + ", " + srcReg + ", 2");
                code.add("  addu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 6:  // (x << 2) + (x << 1)
                code.add("  sll " + dstReg + ", " + srcReg + ", 2");
                code.add("  sll " + temp + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 7:  // (x << 3) - x
                code.add("  sll " + temp + ", " + srcReg + ", 3");
                code.add("  subu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 9:  // (x << 3) + x
                code.add("  sll " + temp + ", " + srcReg + ", 3");
                code.add("  addu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 10: // (x << 3) + (x << 1)
                code.add("  sll " + dstReg + ", " + srcReg + ", 3");
                code.add("  sll " + temp + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 11: // ((x << 2) + x) << 1) + x = (x << 3) + (x << 1) + x
                code.add("  sll " + temp + ", " + srcReg + ", 3");
                code.add("  sll " + temp2 + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + temp + ", " + temp2);
                code.add("  addu " + dstReg + ", " + dstReg + ", " + srcReg);
                break;
            case 12: // (x << 3) + (x << 2)
                code.add("  sll " + dstReg + ", " + srcReg + ", 3");
                code.add("  sll " + temp + ", " + srcReg + ", 2");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 13: // (x << 3) + (x << 2) + x
                code.add("  sll " + temp + ", " + srcReg + ", 3");
                code.add("  sll " + temp2 + ", " + srcReg + ", 2");
                code.add("  addu " + dstReg + ", " + temp + ", " + temp2);
                code.add("  addu " + dstReg + ", " + dstReg + ", " + srcReg);
                break;
            case 14: // (x << 4) - (x << 1) = 16x - 2x = 14x
                code.add("  sll " + dstReg + ", " + srcReg + ", 4");
                code.add("  sll " + temp + ", " + srcReg + ", 1");
                code.add("  subu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 15: // (x << 4) - x
                code.add("  sll " + temp + ", " + srcReg + ", 4");
                code.add("  subu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 17: // (x << 4) + x
                code.add("  sll " + temp + ", " + srcReg + ", 4");
                code.add("  addu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 18: // (x << 4) + (x << 1)
                code.add("  sll " + dstReg + ", " + srcReg + ", 4");
                code.add("  sll " + temp + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 19: // (x << 4) + (x << 1) + x = 16x + 2x + x = 19x
                code.add("  sll " + temp + ", " + srcReg + ", 4");
                code.add("  sll " + temp2 + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + temp + ", " + temp2);
                code.add("  addu " + dstReg + ", " + dstReg + ", " + srcReg);
                break;
            case 20: // (x << 4) + (x << 2)
                code.add("  sll " + dstReg + ", " + srcReg + ", 4");
                code.add("  sll " + temp + ", " + srcReg + ", 2");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 21: // (x << 4) + (x << 2) + x = 16x + 4x + x = 21x
                code.add("  sll " + temp + ", " + srcReg + ", 4");
                code.add("  sll " + temp2 + ", " + srcReg + ", 2");
                code.add("  addu " + dstReg + ", " + temp + ", " + temp2);
                code.add("  addu " + dstReg + ", " + dstReg + ", " + srcReg);
                break;
            case 22: // (x << 4) + (x << 2) + (x << 1) = 16x + 4x + 2x = 22x (4条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 4");
                code.add("  sll " + temp + ", " + srcReg + ", 2");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                code.add("  sll " + temp + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 23: // (x << 5) - (x << 3) - x = 32x - 8x - x = 23x (3条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 5");
                code.add("  sll " + temp + ", " + srcReg + ", 3");
                code.add("  subu " + dstReg + ", " + dstReg + ", " + temp);
                code.add("  subu " + dstReg + ", " + dstReg + ", " + srcReg);
                break;
            case 24: // (x << 4) + (x << 3)
                code.add("  sll " + dstReg + ", " + srcReg + ", 4");
                code.add("  sll " + temp + ", " + srcReg + ", 3");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 25: // (x << 4) + (x << 3) + x
                code.add("  sll " + temp + ", " + srcReg + ", 4");
                code.add("  sll " + temp2 + ", " + srcReg + ", 3");
                code.add("  addu " + dstReg + ", " + temp + ", " + temp2);
                code.add("  addu " + dstReg + ", " + dstReg + ", " + srcReg);
                break;
            case 27: // (x << 5) - (x << 2) - x = 32x - 4x - x = 27x (4条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 5");
                code.add("  sll " + temp + ", " + srcReg + ", 2");
                code.add("  subu " + dstReg + ", " + dstReg + ", " + temp);
                code.add("  subu " + dstReg + ", " + dstReg + ", " + srcReg);
                break;
            case 28: // (x << 5) - (x << 2) = 32x - 4x = 28x (3条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 5");
                code.add("  sll " + temp + ", " + srcReg + ", 2");
                code.add("  subu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 30: // (x << 5) - (x << 1) = 32x - 2x = 30x (3条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 5");
                code.add("  sll " + temp + ", " + srcReg + ", 1");
                code.add("  subu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 31: // (x << 5) - x
                code.add("  sll " + temp + ", " + srcReg + ", 5");
                code.add("  subu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 33: // (x << 5) + x
                code.add("  sll " + temp + ", " + srcReg + ", 5");
                code.add("  addu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 34: // (x << 5) + (x << 1) = 32x + 2x = 34x (3条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 5");
                code.add("  sll " + temp + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 35: // (x << 5) + (x << 1) + x = 32x + 2x + x = 35x (4条)
                code.add("  sll " + temp + ", " + srcReg + ", 5");
                code.add("  sll " + temp2 + ", " + srcReg + ", 1");
                code.add("  addu " + dstReg + ", " + temp + ", " + temp2);
                code.add("  addu " + dstReg + ", " + dstReg + ", " + srcReg);
                break;
            case 36: // (x << 5) + (x << 2) = 32x + 4x = 36x (3条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 5");
                code.add("  sll " + temp + ", " + srcReg + ", 2");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 40: // (x << 5) + (x << 3) = 32x + 8x = 40x (3条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 5");
                code.add("  sll " + temp + ", " + srcReg + ", 3");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 48: // (x << 5) + (x << 4) = 32x + 16x = 48x (3条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 5");
                code.add("  sll " + temp + ", " + srcReg + ", 4");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 63: // (x << 6) - x
                code.add("  sll " + temp + ", " + srcReg + ", 6");
                code.add("  subu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 65: // (x << 6) + x
                code.add("  sll " + temp + ", " + srcReg + ", 6");
                code.add("  addu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            case 100: // (x << 6) + (x << 5) + (x << 2) = 64x + 32x + 4x = 100x (4条)
                code.add("  sll " + dstReg + ", " + srcReg + ", 6");
                code.add("  sll " + temp + ", " + srcReg + ", 5");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                code.add("  sll " + temp + ", " + srcReg + ", 2");
                code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                break;
            case 127: // (x << 7) - x
                code.add("  sll " + temp + ", " + srcReg + ", 7");
                code.add("  subu " + dstReg + ", " + temp + ", " + srcReg);
                break;
            default:
                // 尝试通用分解算法
                return tryGenericDecompose(srcReg, dstReg, m, temp, temp2, negative);
        }
        
        if (negative && !code.isEmpty()) {
            code.add("  subu " + dstReg + ", $zero, " + dstReg);
        }
        
        return code.isEmpty() ? null : code;
    }
    
    /**
     * 通用乘法分解算法
     * 尝试将乘数分解为最多4条指令的移位+加减组合
     */
    private static List<String> tryGenericDecompose(String srcReg, String dstReg, int m,
                                                     String temp, String temp2, boolean negative) {
        // 策略1: 2^k + 2^j + 2^i (3条移位+2条加法=5条，但可以优化到4条)
        // 策略2: 2^k - 2^j (2条移位+1条减法=3条)
        // 策略3: 2^k + 2^j (2条移位+1条加法=3条)
        // 策略4: (2^k + 1) * 2^j 等复合形式
        
        List<String> code = new ArrayList<>();
        
        // 检查 2^k + 2^j + 2^i 模式
        for (int i = 0; i < 20; i++) {
            int pi = 1 << i;
            if (pi > m) break;
            for (int j = i + 1; j < 20; j++) {
                int pj = 1 << j;
                if (pi + pj > m) break;
                int rem = m - pi - pj;
                if (rem > 0 && isPowerOf2(rem)) {
                    int k = log2(rem);
                    if (k > j) {
                        // m = 2^k + 2^j + 2^i, 需要4条指令
                        code.add("  sll " + dstReg + ", " + srcReg + ", " + k);
                        code.add("  sll " + temp + ", " + srcReg + ", " + j);
                        code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                        code.add("  sll " + temp + ", " + srcReg + ", " + i);
                        code.add("  addu " + dstReg + ", " + dstReg + ", " + temp);
                        if (negative) {
                            code.add("  subu " + dstReg + ", $zero, " + dstReg);
                        }
                        return code.size() <= 5 ? code : null;
                    }
                }
            }
        }
        
        // 检查 2^k - 2^j - 2^i 模式
        for (int k = 3; k < 20; k++) {
            int pk = 1 << k;
            if (pk <= m) continue;
            int diff = pk - m;
            for (int i = 0; i < k - 1; i++) {
                int pi = 1 << i;
                if (pi >= diff) break;
                int rem = diff - pi;
                if (rem > 0 && isPowerOf2(rem) && log2(rem) < k) {
                    int j = log2(rem);
                    // m = 2^k - 2^j - 2^i, 需要4条指令
                    code.add("  sll " + dstReg + ", " + srcReg + ", " + k);
                    code.add("  sll " + temp + ", " + srcReg + ", " + j);
                    code.add("  subu " + dstReg + ", " + dstReg + ", " + temp);
                    code.add("  sll " + temp + ", " + srcReg + ", " + i);
                    code.add("  subu " + dstReg + ", " + dstReg + ", " + temp);
                    if (negative) {
                        code.add("  subu " + dstReg + ", $zero, " + dstReg);
                    }
                    return code.size() <= 5 ? code : null;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 检查是否为 2 的幂
     */
    public static boolean isPowerOf2(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    /**
     * 获取 2 的幂次的指数
     */
    public static int log2(int n) {
        return Integer.numberOfTrailingZeros(n);
    }
    
    // 兼容性：保留 getOptimization 方法（返回 null）
    public static Object getOptimization(int multiplier) {
        return null;
    }
    
    // 兼容性：MulItem 和 MulOptResult 类
    public static class MulItem {
        public final boolean isAdd;
        public final int shift;
        public MulItem(boolean isAdd, int shift) {
            this.isAdd = isAdd;
            this.shift = shift;
        }
    }
    
    public static class MulOptResult {
        public final int multiplier;
        public final List<MulItem> items;
        public final int cost;
        public MulOptResult(int multiplier, List<MulItem> items, int cost) {
            this.multiplier = multiplier;
            this.items = items;
            this.cost = cost;
        }
    }
}
