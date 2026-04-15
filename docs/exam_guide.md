## 1. MIPS 常用指令速查表

在后端代码生成涉及修改 MIPS 指令时，请参考以下列表。

### 寄存器约定
| 寄存器 | 名称 | 用途 |
| :--- | :--- | :--- |
| `$0` | `$zero` | 恒为 0 |
| `$v0` - `$v1` | | 函数返回值，syscall 系统调用号 |
| `$a0` - `$a3` | | 函数参数 (前4个) |
| `$t0` - `$t9` | | 临时寄存器 (Caller-saved) |
| `$s0` - `$s7` | | 保存寄存器 (Callee-saved) |
| `$gp` | | 全局指针 |
| `$sp` | | 栈指针 |
| `$fp` | | 帧指针 |
| `$ra` | | 返回地址 |

### 常用指令

#### 算术与逻辑运算
| 指令 | 格式 | 说明 | 示例 |
| :--- | :--- | :--- | :--- |
| `add` | `add rd, rs, rt` | 加法 (带溢出检查) | `add $t0, $t1, $t2` |
| `addu` | `addu rd, rs, rt` | 无符号加法 (无溢出检查) | `addu $t0, $t1, $t2` |
| `sub` | `sub rd, rs, rt` | 减法 | `sub $t0, $t1, $t2` |
| `subu` | `subu rd, rs, rt` | 无符号减法 | `subu $t0, $t1, $t2` |
| `addi` | `addi rt, rs, imm` | 立即数加法 | `addi $t0, $t0, 1` |
| `addiu`| `addiu rt, rs, imm`| 无符号立即数加法 | `addiu $sp, $sp, -4` |
| `mul` | `mul rd, rs, rt` | 乘法 (结果存通用寄存器) | `mul $t0, $t1, $t2` |
| `mult` | `mult rs, rt` | 乘法 (结果存 Hi/Lo) | `mult $t1, $t2` |
| `div` | `div rs, rt` | 除法 (商存 Lo, 余存 Hi) | `div $t1, $t2` |
| `mflo` | `mflo rd` | 取 Lo 寄存器值 (商) | `mflo $t0` |
| `mfhi` | `mfhi rd` | 取 Hi 寄存器值 (余) | `mfhi $t0` |
| `and` | `and rd, rs, rt` | 按位与 | `and $t0, $t1, $t2` |
| `or` | `or rd, rs, rt` | 按位或 | `or $t0, $t1, $t2` |
| `xor` | `xor rd, rs, rt` | 按位异或 | `xor $t0, $t1, $t2` |
| `nor` | `nor rd, rs, rt` | 按位或非 | `nor $t0, $t1, $t2` |
| `andi` | `andi rt, rs, imm` | 立即数按位与 | `andi $t0, $t1, 0xFF` |
| `ori` | `ori rt, rs, imm` | 立即数按位或 | `ori $t0, $t1, 0x0F` |
| `sll` | `sll rd, rt, shamt` | 逻辑左移 | `sll $t0, $t1, 2` |
| `srl` | `srl rd, rt, shamt` | 逻辑右移 | `srl $t0, $t1, 2` |
| `sra` | `sra rd, rt, shamt` | 算术右移 (保留符号位) | `sra $t0, $t1, 2` |

#### 比较指令
| 指令 | 格式 | 说明 | 示例 |
| :--- | :--- | :--- | :--- |
| `slt` | `slt rd, rs, rt` | 小于置 1 (有符号) | `slt $t0, $t1, $t2` |
| `slti` | `slti rt, rs, imm` | 小于立即数置 1 | `slti $t0, $t1, 10` |
| `sltu` | `sltu rd, rs, rt` | 小于置 1 (无符号) | `sltu $t0, $t1, $t2` |
| `seq` | `seq rd, rs, rt` | 等于置 1 (伪指令) | `seq $t0, $t1, $t2` |
| `sne` | `sne rd, rs, rt` | 不等于置 1 (伪指令) | `sne $t0, $t1, $t2` |

#### 跳转与分支指令 (重点)
| 指令 | 格式 | 说明 | 示例 |
| :--- | :--- | :--- | :--- |
| `j` | `j label` | 无条件跳转 | `j loop_start` |
| `jal` | `jal label` | 跳转并链接 (存返回地址到 $ra) | `jal func_foo` |
| `jr` | `jr rs` | 跳转到寄存器地址 (通常用于返回) | `jr $ra` |
| `beq` | `beq rs, rt, label` | 相等跳转 | `beq $t0, $t1, end_loop` |
| `bne` | `bne rs, rt, label` | 不相等跳转 | `bne $t0, $zero, true_block` |
| `bgtz` | `bgtz rs, label` | 大于 0 跳转 | `bgtz $t0, label` |
| `blez` | `blez rs, label` | 小于等于 0 跳转 | `blez $t0, label` |
| `bgez` | `bgez rs, label` | 大于等于 0 跳转 | `bgez $t0, label` |
| `bltz` | `bltz rs, label` | 小于 0 跳转 | `bltz $t0, label` |
| `bgt` | `bgt rs, rt, label` | 大于跳转 (伪指令) | `bgt $t0, $t1, label` |
| `blt` | `blt rs, rt, label` | 小于跳转 (伪指令) | `blt $t0, $t1, label` |
| `bge` | `bge rs, rt, label` | 大于等于跳转 (伪指令) | `bge $t0, $t1, label` |
| `ble` | `ble rs, rt, label` | 小于等于跳转 (伪指令) | `ble $t0, $t1, label` |
| `bgtu` | `bgtu rs, rt, label` | 无符号大于跳转 (伪指令) | `bgtu $t0, $t1, label` |
| `bltu` | `bltu rs, rt, label` | 无符号小于跳转 (伪指令) | `bltu $t0, $t1, label` |
| `bgeu` | `bgeu rs, rt, label` | 无符号大于等于跳转 (伪指令) | `bgeu $t0, $t1, label` |
| `bleu` | `bleu rs, rt, label` | 无符号小于等于跳转 (伪指令) | `bleu $t0, $t1, label` |

#### 访存指令
| 指令 | 格式 | 说明 | 示例 |
| :--- | :--- | :--- | :--- |
| `lw` | `lw rt, offset(base)` | 加载字 (4字节) | `lw $t0, 8($sp)` |
| `sw` | `sw rt, offset(base)` | 存储字 (4字节) | `sw $t0, 4($sp)` |
| `la` | `la rd, label` | 加载地址 (伪指令) | `la $t0, global_var` |
| `li` | `li rd, imm` | 加载立即数 (伪指令) | `li $t0, 100` |

#### 系统调用 (Syscall)
| 服务 | $v0 代码 | 参数 | 结果 |
| :--- | :--- | :--- | :--- |
| print_int | 1 | `$a0` = integer | |
| print_string | 4 | `$a0` = address of string | |
| read_int | 5 | | `$v0` = integer |
| exit | 10 | | |
| print_char | 11 | `$a0` = char | |

#### 系统调用示例代码

```asm
# 1. 打印整数 (print_int)
li $v0, 1           # 加载系统调用号 1
li $a0, 123         # 加载要打印的整数到 $a0
syscall             # 执行系统调用

# 2. 打印字符串 (print_string)
.data
msg: .asciiz "Hello\n"
.text
li $v0, 4           # 加载系统调用号 4
la $a0, msg         # 加载字符串地址到 $a0
syscall

# 3. 读取整数 (read_int)
li $v0, 5           # 加载系统调用号 5
syscall             # 执行后，$v0 中保存读取的整数
move $t0, $v0       # 将读取的值保存到临时寄存器

# 4. 退出程序 (exit)
li $v0, 10          # 加载系统调用号 10
syscall

# 5. 打印字符 (print_char)
li $v0, 11          # 加载系统调用号 11
li $a0, 10          # 加载字符 ASCII 码 (这里是换行符 \n)
syscall
```

---

## 2. 编译器功能扩展指南

当题目要求新增文法或运算符时，请按照以下步骤进行修改。

### 第一步：词法分析 (Frontend)

1.  **修改 `frontend/TokenType.java`**:
    *   在枚举中添加新的 Token 类型。
    *   例如：添加 `BITAND` (`&`)。
    ```java
    public enum TokenType {
        // ...
        BITAND, // &
        // ...
    }
    ```

2.  **修改 `frontend/Lexer.java`**:
    *   在 `KEYWORDS_AND_SYMBOLS` 静态块中注册单字符符号（如果是单字符）。
    *   或者在 `analyze()` 方法的 `switch` 语句中添加处理逻辑。
    *   例如：
    ```java
    // Lexer.java
    case '&': 
        if (match('&')) {
            addToken(TokenType.AND, "&&"); // 现有的 &&
        } else {
            addToken(TokenType.BITAND, "&"); // 新增的 &
        }
        break;
    ```

### 第二步：语法分析 (Frontend)

1.  **修改 `frontend/Parser.java`**:
    *   根据文法优先级，找到对应的解析方法。
    *   **表达式 (Exp)**:
        *   `parseExp` -> `parseAddExp` -> `parseMulExp` -> `parseUnaryExp` -> `parsePrimaryExp`。
        *   如果新增运算符优先级在加法和乘法之间，可能需要插入一个新的解析层级（例如 `parseBitAndExp`）。
        *   如果只是同级新增（例如新增取模 `%`，已存在），则在对应方法（如 `parseMulExp`）的 `while` 循环中添加 `case`。
    *   **语句 (Stmt)**:
        *   修改 `parseStmt` 方法。
        *   例如新增 `repeat-until`，检测 `REPEAT` token，然后递归解析 `Stmt` 和 `Cond`。

### 第三步：抽象语法树 (AST)

1.  **修改/新增 AST 节点 (`ast/` 目录)**:
    *   如果是二元运算，通常复用 `BinaryExp` 或类似的结构，或者在现有的 `MulExp` / `AddExp` 中扩展。
    *   如果是新的语句（如 `RepeatStmt`），建议新建一个类 `ast/RepeatStmt.java`，继承自 `Stmt`。
    ```java
    public class RepeatStmt extends Stmt {
        private Stmt body;
        private Cond cond;
        // 构造函数, getter, toString...
    }
    ```

### 第四步：中间代码生成 (Midend)

1.  **修改 `midend/IRBuilder.java`**:
    *   在 `visit` 方法中处理新的 AST 节点。
    *   **新运算符**:
        *   在 `visitAddExp` 或类似方法中，生成对应的 IR 指令。
        *   LLVM IR 常用指令类在 `midend.ir.values.instructions` 中。
        *   例如位运算：`new BinaryInst(BinaryInst.Op.And, ...)`。
    *   **新控制流**:
        *   参考 `visitIfStmt` 或 `visitForStmt`。
        *   需要创建 `BasicBlock` (基本块)。
        *   使用 `new BranchInst(...)` 进行跳转。
        *   **Repeat-Until 示例逻辑**:
            1.  创建 `loopBodyBB`, `loopCondBB`, `afterLoopBB`。
            2.  从当前块跳转到 `loopBodyBB`。
            3.  `visit(loopBodyBB)`。
            4.  访问循环体语句。
            5.  跳转到 `loopCondBB`。
            6.  `visit(loopCondBB)`。
            7.  访问条件表达式，生成 `icmp` 指令。
            8.  生成条件跳转：`br cond, afterLoopBB, loopBodyBB` (注意 until 是条件为真结束，为假继续)。
            9.  `visit(afterLoopBB)`。

### 第五步：后端代码生成 (Backend)

通常情况下，如果你的 IR 生成使用了标准的 LLVM IR 指令（如 `add`, `sub`, `icmp`, `br`），**不需要修改后端**。现有的 `IRtoMIPSConverter.java` 已经涵盖了大部分指令。

**只有当你生成了特殊的 IR 指令，或者使用了后端尚未支持的指令（如位运算 `and`, `or`, `xor` 可能未完全实现）时，才需要修改。**

1.  **检查 `backend/mips/IRtoMIPSConverter.java`**:
    *   查看 `parseInstruction` 方法中的 `switch (command)`。
    *   确认是否支持你生成的 IR 指令。
    *   **如果不支持**:
        *   添加新的 `case`。
        *   解析操作数。
        *   使用 `textSection.append(...)` 输出对应的 MIPS 指令。
        *   利用 `ensureInReg` 或 `loadOperandToReg` 辅助方法加载操作数到寄存器。

#### 示例：添加位运算 `and` 支持 (如果后端缺失)

假设 IR 中生成了 `%3 = and i32 %1, %2`。

在 `IRtoMIPSConverter.java` 的 `parseInstruction` 中添加：

```java
case "and": {
    String destReg = parts[0]; // %3
    String op1 = parts[parts.length-2]; // %1
    String op2 = parts[parts.length-1]; // %2

    // 获取目标寄存器
    String targetReg = registerMap.getOrDefault(destReg, getTempReg(2));
    
    // 加载操作数1
    String op1Reg = ensureInReg(op1, getTempReg(0));
    
    // 检查操作数2是否为立即数 (andi)
    boolean optimized = false;
    try {
        int imm = Integer.parseInt(op2);
        if (imm >= 0 && imm <= 65535) { // andi 是无符号扩展
            textSection.append("  andi ").append(targetReg).append(", ").append(op1Reg).append(", ").append(imm).append("\n");
            optimized = true;
        }
    } catch (Exception e) {}

    if (!optimized) {
        String op2Reg = ensureInReg(op2, getTempReg(1));
        textSection.append("  and ").append(targetReg).append(", ").append(op1Reg).append(", ").append(op2Reg).append("\n");
    }

    // 如果目标变量溢出到栈，写回
    if (!registerMap.containsKey(destReg)) {
        textSection.append("  sw ").append(targetReg).append(", ").append(stackOffsetMap.get(destReg)).append("($fp)\n");
    }
    break;
}
```

## 3. 常见上机题型思路

###### 1. 新增运算符 (如 `a ** b` 幂运算)
*   **Lexer**: 添加 `**` token。
*   **Parser**: 调整优先级，可能需要新建 `Exp` 层级。
*   **IR**: 生成 `call @pow` (调用库函数) 或展开为循环 (较难)。
*   **Backend**: 如果是 `call`，现有逻辑支持。

###### 2. 新增控制流 (如 `repeat ... until (cond)`)
*   **Lexer**: 添加 `repeat`, `until` 关键字。
*   **Parser**: `parseStmt` 中处理。
*   **IR**: 生成基本块和跳转指令。逻辑类似 `do-while`。
    *   `Label_Loop`:
    *   Execute Body
    *   Calculate Cond
    *   `br Cond, Label_Exit, Label_Loop` (Until Cond is true -> Exit)
*   **Backend**: 无需修改，只要 IR 正确生成 `br`。

###### 3. 格式化输出扩展 (如 `printf` 支持 `%b` 二进制)
*   **Lexer/Parser**: 无需修改 (字符串是字面量)。
*   **IR**: 无需修改。
*   **Backend**: 修改 `IRtoMIPSConverter.java` 中 `printf` 的内联逻辑。
    *   找到处理 `%d` 的地方。
    *   添加对 `%b` 的检测。
    *   实现一个打印二进制的 MIPS 汇编片段 (循环移位 + `andi` + `syscall 11`)。
