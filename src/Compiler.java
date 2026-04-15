/**
 * SysY Compiler - Main Entry Point
 * 
 * Compilation Pipeline:
 *   Source -> Lexer -> Parser -> IRBuilder -> Optimizations -> MIPSConverter -> MIPSOptimizer -> Output
 * 
 * Package Structure:
 *   frontend/       - Lexer, Parser, Token, AST nodes
 *   midend/         - IRBuilder, IR representation, optimization passes
 *   backend/        - MIPS code generation, register allocation, target optimizations
 *   symbol/         - Symbol table management
 */

// Frontend imports
import frontend.Lexer;
import frontend.Token;
import frontend.Parser;
import ast.CompUnit;

// Midend imports
import midend.IRBuilder;
import midend.ir.values.Module;
import midend.optim.*;

// Backend imports
import backend.mips.IRtoMIPSConverter;
import backend.mips.MIPSOptimizer;

// Java standard library
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Main compiler driver class.
 * Orchestrates the compilation pipeline from source to MIPS assembly.
 */
public class Compiler {
    
    // ========== Configuration ==========
    
    /** Enable IR and MIPS level optimizations */
    private static boolean ENABLE_OPTIMIZATION = true;
    
    /** Student information for output file naming */
    private static final String STUDENT_ID = "23371540";
    private static final String STUDENT_NAME = "罗天翼";
    
    // ========== Main Entry Point ==========
    
    public static void main(String[] args) {
        // Parse command line arguments
        parseArgs(args);
        
        // Configure file paths
        CompilerConfig config = new CompilerConfig("testfile.txt");
        
        // Run compilation pipeline
        try {
            compile(config);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    // ========== Compilation Pipeline ==========
    
    /**
     * Main compilation pipeline.
     * Stages: Source -> Lexer -> Parser -> IRBuilder -> Optimize -> CodeGen -> Output
     */
    private static void compile(CompilerConfig config) throws IOException {
        ArrayList<String> errors = new ArrayList<>();
        
        // Read source code
        String sourceCode = new String(Files.readAllBytes(Paths.get(config.inputFile)));
        
        // ========== Stage 1: Lexical Analysis ==========
        System.out.println("[Stage 1] Lexical Analysis...");
        Lexer lexer = new Lexer(sourceCode);
        lexer.analyze();
        List<Token> tokens = lexer.getTokens();
        if (lexer.hasErrors()) {
            errors.addAll(lexer.getErrors());
        }
        
        // ========== Stage 2: Syntax & Semantic Analysis ==========
        System.out.println("[Stage 2] Parsing & Semantic Analysis...");
        Parser parser = new Parser(tokens);
        parser.parse();
        if (parser.hasErrors()) {
            errors.addAll(parser.getErrors());
        }
        
        // ========== Error Handling ==========
        if (!errors.isEmpty()) {
            handleErrors(errors, config.outputFileError);
            return;
        }
        
        // ========== Stage 3: IR Generation ==========
        System.out.println("[Stage 3] IR Generation...");
        CompUnit astRoot = parser.getASTRoot();
        IRBuilder irBuilder = new IRBuilder();
        Module module = irBuilder.visit(astRoot);
        String irBeforeOpt = module.toString();
        
        // ========== Stage 4: IR Optimization ==========
        String irAfterOpt;
        if (ENABLE_OPTIMIZATION) {
            System.out.println("[Stage 4] IR Optimization...");
            runOptimizationPasses(module);
            irAfterOpt = module.toString();
        } else {
            irAfterOpt = irBeforeOpt;
        }
        
        // ========== Stage 5: MIPS Code Generation ==========
        System.out.println("[Stage 5] MIPS Code Generation...");
        IRtoMIPSConverter converterBeforeOpt = new IRtoMIPSConverter(irBeforeOpt);
        String mipsBeforeOpt = converterBeforeOpt.convert();
        
        String mipsAfterOpt;
        if (ENABLE_OPTIMIZATION) {
            IRtoMIPSConverter converterAfterOpt = new IRtoMIPSConverter(irAfterOpt);
            String mipsRaw = converterAfterOpt.convert();
            
            // ========== Stage 6: MIPS Optimization ==========
            System.out.println("[Stage 6] MIPS Optimization...");
            MIPSOptimizer mipsOptimizer = new MIPSOptimizer(mipsRaw);
            mipsAfterOpt = mipsOptimizer.optimize();
        } else {
            mipsAfterOpt = mipsBeforeOpt;
        }
        
        // ========== Stage 7: Output Generation ==========
        System.out.println("[Stage 7] Generating Output Files...");
        writeOutputFiles(config, tokens, parser, irBeforeOpt, irAfterOpt, mipsBeforeOpt, mipsAfterOpt);
        
        System.out.println("Compilation successful!");
    }
    
    // ========== Optimization Passes ==========
    
    /**
     * Run all IR optimization passes in sequence.
     * Pass order is important for best results.
     */
    private static void runOptimizationPasses(Module module) {
        // Phase 1: Prepare for SSA
        new GlobalVariableLocalize().run(module);
        new InlineFunction().run(module);
        
        // Note: Mem2Reg + PhiElimination disabled due to cross-function reference bug
        // Dead code elimination is leaving stores that reference allocas from other functions
        
        // Phase 2: Iterative constant propagation
        for (int i = 0; i < 5; i++) {
            new InstructionSimplification().run(module);
            new ConstantFunctionEval().run(module);
        }
        
        // Phase 3: Main optimizations
        new InstructionSimplification().run(module);
        new DeadCodeElimination().run(module);
        new DeadStoreElimination().run(module);  // Remove stores to never-read allocas
        new GCM().run(module);              // Loop invariant code motion (first pass)
        new LocalMem2Reg().run(module);     // Block-local memory promotion
        new GVN().run(module);              // Global value numbering
        new GCM().run(module);              // Loop invariant code motion (second pass)
        new DeadCodeElimination().run(module);
        new DeadStoreElimination().run(module);  // Final cleanup
    }
    
    // ========== Output Handling ==========
    
    /**
     * Write all output files (lexer, parser, symbol, IR, MIPS).
     */
    private static void writeOutputFiles(
            CompilerConfig config,
            List<Token> tokens,
            Parser parser,
            String irBeforeOpt,
            String irAfterOpt,
            String mipsBeforeOpt,
            String mipsAfterOpt
    ) throws IOException {
        // Lexer output
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(config.outputFileLexer))) {
            for (Token token : tokens) {
                writer.write(token.getType().name() + " " + token.getValue() + "\n");
            }
        }
        
        // Parser output
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(config.outputFileParser))) {
            writer.write(parser.getParseTreeString());
        }
        
        // Symbol table output
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(config.outputFileSymbol))) {
            writer.write(parser.getSymbolString());
        }
        
        // IR output
        writeToFile(config.outputFileLlvm, ENABLE_OPTIMIZATION ? irAfterOpt : irBeforeOpt);
        
        // MIPS output
        writeToFile(config.outputFileMips, ENABLE_OPTIMIZATION ? mipsAfterOpt : mipsBeforeOpt);
        
        // Optimization comparison files
        if (ENABLE_OPTIMIZATION) {
            writeToFile(config.llvmBeforeOpt, irBeforeOpt);
            writeToFile(config.llvmAfterOpt, irAfterOpt);
            writeToFile(config.mipsBeforeOpt, mipsBeforeOpt);
            writeToFile(config.mipsAfterOpt, mipsAfterOpt);
            System.out.println("Optimization files written.");
        }
    }
    
    /**
     * Handle compilation errors by sorting and writing to error file.
     */
    private static void handleErrors(ArrayList<String> errors, String errorFile) {
        System.out.println("Errors found during compilation. See " + errorFile + " for details.");
        
        // Sort errors by line number
        errors.sort((e1, e2) -> {
            try {
                int line1 = Integer.parseInt(e1.split(" ")[0]);
                int line2 = Integer.parseInt(e2.split(" ")[0]);
                return Integer.compare(line1, line2);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                return e1.compareTo(e2);
            }
        });
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(errorFile))) {
            for (String error : errors) {
                writer.write(error + "\n");
            }
        } catch (IOException e) {
            System.err.println("Error writing error file: " + e.getMessage());
        }
    }
    
    // ========== Utility Methods ==========
    
    private static void parseArgs(String[] args) {
        for (String arg : args) {
            if (arg.equals("-O") || arg.equals("--optimize")) {
                ENABLE_OPTIMIZATION = true;
            } else if (arg.equals("-O0") || arg.equals("--no-optimize")) {
                ENABLE_OPTIMIZATION = false;
            }
        }
    }
    
    private static void writeToFile(String filename, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(content);
        } catch (IOException e) {
            System.err.println("Error writing file " + filename + ": " + e.getMessage());
        }
    }
    
    // ========== Configuration Class ==========
    
    /**
     * Holds all file path configuration for compilation.
     */
    private static class CompilerConfig {
        final String inputFile;
        final String outputFileLexer = "lexer.txt";
        final String outputFileParser = "parser.txt";
        final String outputFileError = "error.txt";
        final String outputFileSymbol = "symbol.txt";
        final String outputFileLlvm = "llvm_ir.txt";
        final String outputFileMips = "mips.txt";
        
        final String llvmBeforeOpt;
        final String llvmAfterOpt;
        final String mipsBeforeOpt;
        final String mipsAfterOpt;
        
        CompilerConfig(String inputFile) {
            this.inputFile = inputFile;
            String testNum = extractTestfileNumber(inputFile);
            String prefix = "testfile" + testNum + "_" + STUDENT_ID + "_" + STUDENT_NAME + "_";
            this.llvmBeforeOpt = prefix + "优化前中间代码.txt";
            this.llvmAfterOpt = prefix + "优化后中间代码.txt";
            this.mipsBeforeOpt = prefix + "优化前目标代码.txt";
            this.mipsAfterOpt = prefix + "优化后目标代码.txt";
        }
        
        private static String extractTestfileNumber(String filename) {
            String name = filename.replace(".txt", "").replace("testfile", "");
            return name.isEmpty() ? "" : name;
        }
    }
}
