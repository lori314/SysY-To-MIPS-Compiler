package backend.target;

public class MagicNumber {
    public int multiplier;
    public int shift;
    
    // 计算 magic number 的算法 (参考 Hacker's Delight)
    public static MagicNumber getMagicNumber(int d) {
        MagicNumber mag = new MagicNumber();
        long two31 = 1L << 31; // 2^31
        long ad = Math.abs(d);
        long t = two31 + (d >>> 31);
        long anc = t - 1 - t % ad;
        int p = 31;
        long q1 = two31 / anc;   // 使用 anc 而不是 ad
        long r1 = two31 % anc;   // 使用 anc 而不是 ad
        long q2 = two31 / ad;
        long r2 = two31 % ad;
        long delta;
        
        do {
            p++;
            q1 = 2 * q1;
            r1 = 2 * r1;
            if (r1 >= anc) {
                q1++;
                r1 = r1 - anc;
            }
            q2 = 2 * q2;
            r2 = 2 * r2;
            if (r2 >= ad) {
                q2++;
                r2 = r2 - ad;
            }
            delta = ad - r2;
        } while (q1 < delta || (q1 == delta && r1 == 0));
        
        long res = q2 + 1;
        if (d < 0) res = -res;
        
        mag.multiplier = (int) res; // 强转回 int，对应 MIPS 寄存器行为
        mag.shift = p - 32;
        return mag;
    }
}
