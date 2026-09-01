# SysY to MIPS Compiler

一个使用 Java 实现的 SysY 编译器，将 SysY 源程序编译为 MIPS 汇编。项目完成了从词法/语法分析、AST 构建，到中间代码生成、优化和目标代码生成的完整流程。

> SysY 是 C 语言的一个教学子集。这个项目来自编译课程，后期又继续做了一些优化器和后端实验。

## 编译流程

```text
SysY Source
    ↓
Lexer / Parser
    ↓
AST + Symbol Table
    ↓
LLVM-style IR
    ↓
IR Optimization
    ↓
MIPS Code Generation
    ↓
MIPS Peephole / Target Optimization
```

主入口位于 `src/Compiler.java`。

## 实现内容

### Frontend

- 词法分析与 Token 生成
- 递归下降语法分析
- AST 构建
- 符号表与语义错误检查

相关代码：

```text
src/frontend/
src/ast/
src/symbol/
```

### Midend

前端 AST 会被转换为 LLVM 风格的中间表示，优化逻辑集中在 `src/midend/optim/`。

当前默认开启的优化流程包括：

- 全局变量局部化
- 函数内联
- 指令化简与常量函数求值
- Dead Code Elimination
- Dead Store Elimination
- Local Mem2Reg
- Global Value Numbering (GVN)
- Global Code Motion (GCM)

其中部分 Pass 会重复执行，以继续清理由前一轮优化产生的冗余代码。

仓库中也保留了完整 `Mem2Reg` 和 Phi 相关处理，但跨函数引用场景下仍有正确性问题，因此当前默认流程使用 `LocalMem2Reg`。

### Backend

后端将中间代码转换为 MIPS，并处理函数栈帧、参数传递、局部变量和全局数据。

当前代码生成路径中包含一套基于活跃区间的线性扫描寄存器分配：

- `$s0-$s7` 用于需要跨函数调用保留的值
- `$t4-$t9`、`$v1` 用于不跨调用的临时值
- `$t0-$t3` 保留给代码生成过程使用
- 寄存器不足时将值 spill 到栈
- 分配时会参考循环深度和使用次数，提高循环内变量的保留优先级

另外还实现了若干目标相关优化，例如：

- 常数乘法的移位/加减替换
- 2 的幂除法优化
- 常数除法的 Magic Number 优化
- MIPS 层的简单 peephole 优化

`src/backend/alloc/` 中还保留了单独的 `LinearScanAllocator` 和 `GraphColoringAllocator` 实验实现；当前默认代码生成路径使用的是 `IRtoMIPSConverter` 内部的线性扫描分配逻辑，而不是这两个独立类。

## 目录结构

```text
.
├── src/
│   ├── Compiler.java
│   ├── frontend/       # Lexer / Parser / Token
│   ├── ast/            # AST nodes
│   ├── symbol/         # Symbol table
│   ├── midend/
│   │   ├── ir/         # IR representation
│   │   └── optim/      # Optimization passes
│   └── backend/
│       ├── mips/       # MIPS code generation and peephole optimization
│       ├── target/     # Mul / Div target optimizations
│       └── alloc/      # Experimental register allocators
└── docs/
    └── exam_guide.md
```

## 编译与运行

需要 Java 环境。在仓库根目录执行：

```bash
cd src
javac Compiler.java
java Compiler
```

程序默认读取当前目录下的 `testfile.txt`。

默认开启优化，也可以显式关闭：

```bash
java Compiler -O0
```

运行后会生成词法、语法、符号表、IR 和 MIPS 等输出文件，包括：

```text
lexer.txt
parser.txt
error.txt
symbol.txt
llvm_ir.txt
mips.txt
```

启用优化时还会额外输出优化前后的 IR 和 MIPS，方便对比优化效果。

## 实验实现

除默认编译链外，仓库还保留了课程后期尝试过的几套实现：

- 使用 `LocalMem2Reg`，完整 `Mem2Reg + PhiElimination` 暂未接入默认流程；
- 使用后端内部的线性扫描寄存器分配；
- 独立的图着色分配器仍作为实验实现保留。
