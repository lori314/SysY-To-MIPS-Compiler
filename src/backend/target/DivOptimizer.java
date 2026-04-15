package backend.target;

import java.util.ArrayList;
import java.util.List;

/**
 * 除法和取模常数优化器
 * 
 * 实现了 Hacker's Delight 中描述的 Magic Number 算法，
 * 将除以常数的操作转换为乘法和移位操作。
 * 
 * 对于有符号除法 n / d：
 * 1. 如果 d 是 2 的幂次，使用移位优化
 * 2. 否则使用 Magic Number 算法
 * 
 * 核心思想：n / d ≈ (n * magic) >> (32 + shift)
 */
public class DivOptimizer {
    
    /**
     * Magic Number 结构
     */
    public static class MagicResult {
        public final int multiplier;  // 魔数
        public final int shift;       // 右移位数
        public final boolean addFlag; // 是否需要加被除数修正
        
        public MagicResult(int multiplier, int shift, boolean addFlag) {
            this.multiplier = multiplier;
            this.shift = shift;
            this.addFlag = addFlag;
        }
    }
    
    /**
     * 计算有符号除法的 Magic Number
     * 参考：Hacker's Delight, Chapter 10
     * 
     * @param d 除数（必须 > 1）
     * @return Magic Number 结果
     */
    public static MagicResult getMagicSigned(int d) {
        if (d == 0) throw new IllegalArgumentException("Division by zero");
        if (d == 1) return null; // 不需要优化
        if (d == -1) return null; // 直接取负
        
        int ad = Math.abs(d);
        long two31 = 1L << 31;
        
        // 计算 t = 2^31 + (d < 0 ? 1 : 0)
        long t = two31 + (d >>> 31);
        
        // 计算 anc = t - 1 - t % ad
        long anc = t - 1 - t % ad;
        
        int p = 31;
        long q1 = two31 / anc;   // q1 = 2^p / anc
        long r1 = two31 % anc;   // r1 = rem(2^p, anc)
        long q2 = two31 / ad;    // q2 = 2^p / |d|
        long r2 = two31 % ad;    // r2 = rem(2^p, |d|)
        long delta;
        
        do {
            p++;
            q1 = 2 * q1;         // q1 = 2^p / anc
            r1 = 2 * r1;         // r1 = rem(2^p, anc)
            if (r1 >= anc) {
                q1++;
                r1 -= anc;
            }
            q2 = 2 * q2;         // q2 = 2^p / |d|
            r2 = 2 * r2;         // r2 = rem(2^p, |d|)
            if (r2 >= ad) {
                q2++;
                r2 -= ad;
            }
            delta = ad - r2;
        } while (q1 < delta || (q1 == delta && r1 == 0));
        
        long mag = q2 + 1;
        if (d < 0) mag = -mag;
        
        int shift = p - 32;
        int multiplier = (int) mag;
        
        // 判断是否需要 add flag（当乘数溢出时）
        boolean addFlag = (d > 0 && mag < 0);
        boolean subFlag = (d < 0 && mag > 0);
        
        return new MagicResult(multiplier, shift, addFlag || subFlag);
    }
    
    /**
     * 生成有符号除法的优化 MIPS 代码
     * 
     * @param dividendReg 被除数寄存器
     * @param targetReg 目标寄存器
     * @param divisor 除数常量
     * @param tempRegs 临时寄存器数组（至少 2 个）
     * @return MIPS 代码行列表，如果无法优化则返回 null
     */
    public static List<String> generateOptimizedDiv(String dividendReg, String targetReg,
                                                     int divisor, String[] tempRegs) {
        if (divisor == 0) return null;
        
        List<String> code = new ArrayList<>();
        String temp0 = tempRegs[0];
        String temp1 = tempRegs.length > 1 ? tempRegs[1] : targetReg;
        
        // 特殊情况：除以 1
        if (divisor == 1) {
            code.add("  move " + targetReg + ", " + dividendReg);
            return code;
        }
        
        // 特殊情况：除以 -1
        if (divisor == -1) {
            code.add("  subu " + targetReg + ", $zero, " + dividendReg);
            return code;
        }
        
        int absDivisor = Math.abs(divisor);
        
        // 如果是 2 的幂次
        if (MulOptimizer.isPowerOf2(absDivisor)) {
            int k = MulOptimizer.log2(absDivisor);
            
            // 有符号除法需要修正负数的截断行为
            // (x + ((x >> 31) >>> (32-k))) >> k
            code.add("  sra " + temp0 + ", " + dividendReg + ", 31");
            if (k > 0) {
                code.add("  srl " + temp0 + ", " + temp0 + ", " + (32 - k));
            }
            code.add("  addu " + temp0 + ", " + dividendReg + ", " + temp0);
            code.add("  sra " + targetReg + ", " + temp0 + ", " + k);
            
            // 如果除数是负数，结果取反
            if (divisor < 0) {
                code.add("  subu " + targetReg + ", $zero, " + targetReg);
            }
            
            return code;
        }
        
        // 使用 Magic Number 算法
        MagicResult mag = getMagicSigned(divisor);
        if (mag == null) return null;
        
        // 加载魔数
        code.add("  li " + temp0 + ", " + mag.multiplier);
        
        // 执行乘法：Hi = (被除数 * 魔数) 的高 32 位
        code.add("  mult " + dividendReg + ", " + temp0);
        code.add("  mfhi " + temp0);
        
        // 如果需要修正（乘数溢出）
        if (mag.addFlag) {
            if (divisor > 0) {
                code.add("  addu " + temp0 + ", " + temp0 + ", " + dividendReg);
            } else {
                code.add("  subu " + temp0 + ", " + temp0 + ", " + dividendReg);
            }
        }
        
        // 执行移位
        if (mag.shift > 0) {
            code.add("  sra " + temp0 + ", " + temp0 + ", " + mag.shift);
        }
        
        // 符号位修正：对于负数被除数，需要加 1
        // q += (dividend >>> 31)
        code.add("  srl " + temp1 + ", " + dividendReg + ", 31");
        code.add("  addu " + targetReg + ", " + temp0 + ", " + temp1);
        
        return code;
    }
    
    /**
     * 生成有符号取模的优化 MIPS 代码
     * r = a - (a / d) * d
     * 
     * @param dividendReg 被除数寄存器
     * @param targetReg 目标寄存器
     * @param divisor 除数常量
     * @param tempRegs 临时寄存器数组（至少 3 个）
     * @return MIPS 代码行列表，如果无法优化则返回 null
     */
    public static List<String> generateOptimizedRem(String dividendReg, String targetReg,
                                                     int divisor, String[] tempRegs) {
        if (divisor == 0) return null;
        
        List<String> code = new ArrayList<>();
        String temp0 = tempRegs[0];
        String temp1 = tempRegs.length > 1 ? tempRegs[1] : targetReg;
        String temp2 = tempRegs.length > 2 ? tempRegs[2] : temp0;
        
        // 特殊情况：除以 1 或 -1，余数为 0
        if (divisor == 1 || divisor == -1) {
            code.add("  li " + targetReg + ", 0");
            return code;
        }
        
        int absDivisor = Math.abs(divisor);
        
        // 如果是 2 的幂次
        if (MulOptimizer.isPowerOf2(absDivisor)) {
            int k = MulOptimizer.log2(absDivisor);
            
            // r = a - (a / d) * d
            // 先计算 a / d
            code.add("  sra " + temp0 + ", " + dividendReg + ", 31");
            if (k > 0) {
                code.add("  srl " + temp0 + ", " + temp0 + ", " + (32 - k));
            }
            code.add("  addu " + temp0 + ", " + dividendReg + ", " + temp0);
            code.add("  sra " + temp0 + ", " + temp0 + ", " + k);
            
            // 再计算 (a / d) * d
            code.add("  sll " + temp0 + ", " + temp0 + ", " + k);
            
            // r = a - quotient * d
            code.add("  subu " + targetReg + ", " + dividendReg + ", " + temp0);
            
            return code;
        }
        
        // 非 2 的幂次：使用除法优化结果计算
        // 先保存被除数
        code.add("  move " + temp2 + ", " + dividendReg);
        
        // 计算 quotient
        List<String> divCode = generateOptimizedDiv(dividendReg, temp0, divisor, 
                                                     new String[]{temp1, targetReg});
        if (divCode == null) return null;
        code.addAll(divCode);
        
        // 计算 quotient * divisor
        List<String> mulCode = MulOptimizer.generateOptimizedMul(temp0, temp1, 
                                                                  absDivisor, new String[]{targetReg});
        if (mulCode != null) {
            code.addAll(mulCode);
        } else {
            code.add("  li " + targetReg + ", " + absDivisor);
            code.add("  mul " + temp1 + ", " + temp0 + ", " + targetReg);
        }
        
        // r = a - quotient * divisor
        code.add("  subu " + targetReg + ", " + temp2 + ", " + temp1);
        
        return code;
    }
}
