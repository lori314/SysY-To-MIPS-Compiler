package frontend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private int position = 0;
    private int line = 1;
    private final List<Token> tokens = new ArrayList<>();
    private static final Map<String, TokenType> KEYWORDS_AND_SYMBOLS = new HashMap<>();
    
    // 用于记录词法错误
    private final List<String> errors = new ArrayList<>();

    // 使用静态代码块初始化所有关键字和单字符符号的映射表
    static {
        // Keywords
        KEYWORDS_AND_SYMBOLS.put("const", TokenType.CONSTTK);
        KEYWORDS_AND_SYMBOLS.put("int", TokenType.INTTK);
        KEYWORDS_AND_SYMBOLS.put("static", TokenType.STATICTK);
        KEYWORDS_AND_SYMBOLS.put("break", TokenType.BREAKTK);
        KEYWORDS_AND_SYMBOLS.put("continue", TokenType.CONTINUETK);
        KEYWORDS_AND_SYMBOLS.put("if", TokenType.IFTK);
        KEYWORDS_AND_SYMBOLS.put("else", TokenType.ELSETK);
        KEYWORDS_AND_SYMBOLS.put("for", TokenType.FORTK);
        KEYWORDS_AND_SYMBOLS.put("return", TokenType.RETURNTK);
        KEYWORDS_AND_SYMBOLS.put("void", TokenType.VOIDTK);
        KEYWORDS_AND_SYMBOLS.put("main", TokenType.MAINTK);
        KEYWORDS_AND_SYMBOLS.put("printf", TokenType.PRINTFTK);
        
        // Single-character Symbols
        KEYWORDS_AND_SYMBOLS.put("+", TokenType.PLUS);
        KEYWORDS_AND_SYMBOLS.put("-", TokenType.MINU);
        KEYWORDS_AND_SYMBOLS.put("*", TokenType.MULT);
        KEYWORDS_AND_SYMBOLS.put("%", TokenType.MOD);
        KEYWORDS_AND_SYMBOLS.put(";", TokenType.SEMICN);
        KEYWORDS_AND_SYMBOLS.put(",", TokenType.COMMA);
        KEYWORDS_AND_SYMBOLS.put("(", TokenType.LPARENT);
        KEYWORDS_AND_SYMBOLS.put(")", TokenType.RPARENT);
        KEYWORDS_AND_SYMBOLS.put("[", TokenType.LBRACK);
        KEYWORDS_AND_SYMBOLS.put("]", TokenType.RBRACK);
        KEYWORDS_AND_SYMBOLS.put("{", TokenType.LBRACE);
        KEYWORDS_AND_SYMBOLS.put("}", TokenType.RBRACE);
    }


    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public ArrayList<String> getErrors() {
        return (ArrayList<String>) errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    // 主分析函数
    public void analyze() {
        while (!isAtEnd()) {
            char c = advance();
            switch (c) {
                // 单字符分隔符
                case '(': addToken(TokenType.LPARENT, "("); break;
                case ')': addToken(TokenType.RPARENT, ")"); break;
                case '[': addToken(TokenType.LBRACK, "["); break;
                case ']': addToken(TokenType.RBRACK, "]"); break;
                case '{': addToken(TokenType.LBRACE, "{"); break;
                case '}': addToken(TokenType.RBRACE, "}"); break;
                case ',': addToken(TokenType.COMMA, ","); break;
                case ';': addToken(TokenType.SEMICN, ";"); break;
                
                // 单字符或双字符运算符
                case '+': addToken(TokenType.PLUS, "+"); break;
                case '-': addToken(TokenType.MINU, "-"); break;
                case '*': addToken(TokenType.MULT, "*"); break;
                case '%': addToken(TokenType.MOD, "%"); break;
                
                case '<': 
                    if (match('=')) {
                        addToken(TokenType.LEQ, "<=");
                    } else {
                        addToken(TokenType.LSS, "<");
                    }
                    break;
                case '>': 
                    if (match('=')) {
                        addToken(TokenType.GEQ, ">=");
                    } else {
                        addToken(TokenType.GRE, ">");
                    }
                    break;
                case '=': 
                    if (match('=')) {
                        addToken(TokenType.EQL, "==");
                    } else {
                        addToken(TokenType.ASSIGN, "=");
                    }
                    break;
                case '!': 
                    if (match('=')) {
                        addToken(TokenType.NEQ, "!=");
                    } else {
                        addToken(TokenType.NOT, "!");
                    }
                    break;
                // 逻辑运算符 & 和 |，需要处理错误情况
                case '&':
                    if (match('&')) {
                        addToken(TokenType.AND, "&&");
                    } else {
                        addToken(TokenType.AND, "&&");
                        logError("a");
                    }
                    break;
                case '|':
                    if (match('|')) {
                        addToken(TokenType.OR, "||");
                    } else {
                        addToken(TokenType.OR, "||");
                        logError("a"); // 词法错误 a
                    }
                    break;

                // 除号或注释
                case '/':
                    if (match('/')) { // 单行注释
                        while (peek() != '\n' && !isAtEnd()) {
                            advance();
                        }
                    } else if (match('*')) { // 多行注释
                        multilineComment();
                    } else {
                        addToken(TokenType.DIV, "/");
                    }
                    break;
                
                // 字符串常量
                case '"':
                    stringLiteral();
                    break;

                // 空白符
                case ' ':
                case '\r':
                case '\t':
                    break; // 忽略
                case '\n':
                    line++; // 换行
                    break;

                default:
                    if (isDigit(c)) {
                        number();
                    } else if (isAlpha(c)) {
                        identifier();
                    } else {
                        // 如果遇到其他无法识别的字符，也可以作为词法错误
                        // logError("unrecognized_char_error_code");
                    }
                    break;
            }
        }
    }

    // --- 单词处理函数 ---

    private void identifier() {
        int start = position - 1;
        while (isAlphaNumeric(peek())) {
            advance();
        }
        String text = source.substring(start, position);
        TokenType type = KEYWORDS_AND_SYMBOLS.getOrDefault(text, TokenType.IDENFR);
        addToken(type, text);
    }

    private void number() {
        int start = position - 1;
        while (isDigit(peek())) {
            advance();
        }
        String value = source.substring(start, position);
        addToken(TokenType.INTCON, value);
    }

    private void stringLiteral() {
        int start = position; // afrer the opening "
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }
        if (isAtEnd()) {
            logError("u"); // 词法错误 u
            return;
        }
        String value = source.substring(start, position);
        advance(); // consume the closing "
        addToken(TokenType.STRCON, "\"" + value + "\"");
    }

    private void multilineComment() {
        while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }
        if (isAtEnd()) {
            logError("u");
        }
        advance(); // consume '*'
        advance(); // consume '/'
    }


    // --- 辅助函数 ---

    private boolean isAtEnd() { return position >= source.length(); }
    private char advance() { return source.charAt(position++); }
    private char peek() { return isAtEnd() ? '\0' : source.charAt(position); }
    private char peekNext() { return position + 1 >= source.length() ? '\0' : source.charAt(position + 1); }

    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(position) != expected) {
            return false;
        }
        position++;
        return true;
    }

    private void addToken(TokenType type, String value) {
        tokens.add(new Token(type, value, line));
    }

    private void logError(String errorCode) {
        errors.add(line + " " + errorCode);
    }
    
    private boolean isDigit(char c) { return c >= '0' && c <= '9'; }
    private boolean isAlpha(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'; }
    private boolean isAlphaNumeric(char c) { return isAlpha(c) || isDigit(c); }
}