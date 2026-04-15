# SysY to MIPS Compiler

这是一个将 SysY 语言（C语言的子集）编译到 MIPS 汇编语言的编译器项目。

## 主要特性

- **前端 (Frontend)**：实现词法分析和语法分析，生成抽象语法树 (AST)。
- **中端 (Midend)**：生成 LLVM IR，并包含多种优化 Pass（如 SSA 生成、Mem2Reg、GVN、死代码消除等）。
- **后端 (Backend)**：从 LLVM IR 生成目标 MIPS 汇编代码，包含图染色/线性扫描寄存器分配，以及针对乘除法的指令优化。

## 目录结构

- `src/`: 核心源代码
  - `Compiler.java`: 编译器主入口。
  - `frontend/`: 词法分析 (Lexer) 与语法分析 (Parser)。
  - `ast/`: 抽象语法树节点。
  - `midend/`: 中间代码生成与优化。
  - `backend/`: MIPS 代码生成与寄存器分配。
  - `symbol/`: 符号表管理。
- `docs/`: 项目相关文档与考试指南。

## 编译与运行

进入 `src` 目录，使用 `javac` 编译整个项目，然后执行主程序，默认会读取相应的输入文件：

```bash
cd src
javac Compiler.java
java Compiler
```
