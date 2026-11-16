package net.zaminmc.zea.search;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The inventory search bar's calculator, rebuilt on the grammar of
 * NotEnoughCalculator (Rijzzz' JEI search-bar calculator, LGPL-3.0):
 * numbers, {@code + - * / % ^ !}, the letter {@code x} as
 * multiplication, implicit multiplication ({@code 2(3+4)},
 * {@code 2sqrt(4)}), parentheses, percentages ({@code 100+10%} is
 * 110), bitwise {@code & | ~ << >>}, binary/hex/octal literals
 * ({@code 0b101 0x1F 0o17}), number units ({@code 2k}, {@code 10
 * stacks}), the constants {@code pi} and {@code e}, {@code ans} (the
 * last result), session variables ({@code $x = 5}, then {@code $x *
 * 2}), and the math functions NEC carries minus its SkyBlock tables:
 * {@code sqrt abs floor ceil round log ln sin cos tan rad deg min max
 * clamp avg gcd lcm xor pct hex bin oct fmt}.
 *
 * <p>The ZEA adaptation drops NEC's SkyBlock-specific surface — the
 * skill and slayer XP tables, the bazaar/auction taxes, the
 * enchanted/storage units ({@code e h sc dc eb}) — and keeps what
 * makes sense next to a general inventory: coin multipliers
 * ({@code k m b t}), stacks ({@code s st stack stacks} = 64) and the
 * whole expression language. NEC persists its {@code $} variables to
 * its config; ZEA keeps them for the game session, which is how long
 * a search-bar session is meant to last.
 *
 * <p>Two behaviors are NEC's and deliberate, not accidents:
 *
 * <ul>
 * <li><b>A bare number shows nothing.</b> NEC treats {@code 64} as an
 * item-count search, not a calculation; a query must carry an
 * operator, a parenthesis, a function, a constant, a variable or a
 * unit before a result appears. {@link #result(String)} applies the
 * same {@link #looksLikeCalculation} gate before parsing, so typing
 * {@code 2} no longer echoes {@code = 2} — the complaint this
 * rebuild fixes.
 * <li><b>Anything the engine does not fully understand shows
 * nothing.</b> An unfinished expression ({@code 2+}), an unbalanced
 * parenthesis, a stray letter, division by zero — every failure path
 * funnels into an empty Optional rather than a guess.
 * </ul>
 *
 * <p>Evaluation is NEC's shape — a lexer, an implicit-multiplication
 * pass, then a recursive-descent walk with NEC's precedence chain
 * (assignment, {@code |}, {@code &}, shifts, {@code + -},
 * {@code * / %}, {@code ^} right-associative, unary, factorial,
 * primary) — over {@link BigDecimal} with a 50-digit context, so
 * {@code 1/3} resolves and coin-size numbers stay exact. Formatting
 * groups digits, caps at ten fraction digits and never uses
 * scientific notation; {@code hex()/bin()/oct()/fmt()} and
 * {@code 0x/0b/0o} literals carry a radix that reshapes the output
 * ({@code hex(255)} shows {@code 0xFF}, {@code fmt(1500000)} shows
 * {@code 1.5m}).
 */
public final class Calculator {
        /** Everything evaluates in this context; 50 significant digits, like NEC. */
        private static final MathContext MATH = new MathContext(50, RoundingMode.HALF_UP);

        /** Fraction digits a formatted result keeps (NEC's decimalPrecision default). */
        private static final int FRACTION_DIGITS = 10;

        /** Factorials and exponents are capped like NEC's, so input cannot stall the frame. */
        private static final int MAX_FACTORIAL = 1000;
        private static final int MAX_EXPONENT = 1000;

        /** Input longer than this is refused outright — the edit box caps input at 64 anyway. */
        public static final int MAX_INPUT_LENGTH = 64;

        /** Coin multipliers and stacks: the units that survive the SkyBlock cut. */
        private static final Map<String, BigDecimal> UNITS = Map.of(
                        "k", new BigDecimal("1000"),
                        "m", new BigDecimal("1000000"),
                        "b", new BigDecimal("1000000000"),
                        "t", new BigDecimal("1000000000000"),
                        "s", new BigDecimal("64"),
                        "st", new BigDecimal("64"),
                        "stack", new BigDecimal("64"),
                        "stacks", new BigDecimal("64"));

        /** Every function the parser accepts (NEU's set minus the SkyBlock tables). */
        private static final List<String> FUNCTIONS = List.of(
                        // One argument.
                        "sqrt", "abs", "floor", "ceil", "round", "log", "ln",
                        "sin", "cos", "tan", "rad", "deg", "hex", "bin", "oct", "fmt",
                        // Exactly two.
                        "min", "max", "gcd", "lcm", "xor", "pct",
                        // Exactly three.
                        "clamp",
                        // Any number.
                        "avg");

        /** Names a $ variable may never take: constants, functions, units. */
        private static final List<String> RESERVED = buildReserved();

        // NEC's looksLikeCalculation patterns, minus the SkyBlock units
        // and the Minecraft-item keyword list (ZEA's calculator mode
        // never doubles as a live item filter, so there is nothing for a
        // word query to collide with — an operatorless word simply parses
        // to nothing).
        private static final Pattern NUMBER_ONLY = Pattern.compile("^\\s*\\d+\\.?\\d*\\s*$");
        private static final Pattern OPERATOR = Pattern.compile(".*(?:[+\\-*/^%!&|~xX]|<<|>>).*");
        private static final Pattern PAREN = Pattern.compile(".*[()].*");
        private static final Pattern FUNCTION_CALL = Pattern
                        .compile(".*(?i:" + String.join("|", FUNCTIONS) + ")\\s*\\(");
        private static final Pattern VARIABLE_REFERENCE = Pattern.compile("(?i)(?:^|.*\\b)(ans|pi|e|\\$\\w+)(?:\\b.*|$)");
        private static final Pattern UNIT_SUFFIX = Pattern
                        .compile(".*\\d+\\s*(?i:" + String.join("|", UNITS.keySet()) + ")(?:\\s|$|[+\\-*/^%xX()])");
        private static final Pattern RADIX_LITERAL = Pattern.compile("^\\s*0[bxo].*", Pattern.CASE_INSENSITIVE);

        // Session state. NEC keeps these on its evaluator instance; ZEA's
        // calculator is static, so they are static and resettable.
        private static BigDecimal lastAnswer = BigDecimal.ZERO;
        private static final Map<String, BigDecimal> variables = new HashMap<>();

        private Calculator() {
        }

        private static List<String> buildReserved() {
                List<String> reserved = new ArrayList<>(FUNCTIONS);
                reserved.addAll(UNITS.keySet());
                reserved.add("ans");
                reserved.add("pi");
                reserved.add("e");
                return List.copyOf(reserved);
        }

        // ------------------------------------------------------------------
        // Public API
        // ------------------------------------------------------------------

        /**
         * The result the search bar shows for an input, or empty when the
         * input is not a complete calculation the gate accepts: bare
         * numbers, plain words and unfinished expressions all show
         * nothing. A successful evaluation also becomes {@code ans}.
         */
        public static Optional<String> result(String input) {
                return evaluate(input).map(node -> formatNode(node));
        }

        /**
         * The evaluated value, or empty when the input is not a complete
         * calculation. The gate runs first, so this never echoes a bare
         * number back.
         */
        public static Optional<BigDecimal> compute(String input) {
                return evaluate(input).map(Node::value);
        }

        /**
         * The value as the search bar shows a plain (radix-less) result:
         * grouped digits, at most ten fraction digits, no trailing zeros,
         * never scientific notation.
         */
        public static String format(BigDecimal value) {
                BigDecimal scaled = value.setScale(FRACTION_DIGITS, RoundingMode.HALF_UP).stripTrailingZeros();
                return insertCommas(scaled.toPlainString());
        }

        /** Clears {@code ans} and every {@code $} variable (tests; a fresh session). */
        public static void resetSession() {
                lastAnswer = BigDecimal.ZERO;
                variables.clear();
        }

        /**
         * NEC's {@code looksLikeCalculation} gate, adapted: a query is a
         * calculation when it carries a constant, an operator, a
         * parenthesis, a function call, a variable or a number unit — and
         * is not just a bare number ({@code 64} is an item count, not
         * math).
         */
        static boolean looksLikeCalculation(String input) {
                String cleaned = clean(input);
                if (cleaned.isEmpty() || cleaned.length() > MAX_INPUT_LENGTH) {
                        return false;
                }
                if (cleaned.equalsIgnoreCase("e") || cleaned.equalsIgnoreCase("pi") || cleaned.equalsIgnoreCase("ans")) {
                        return true;
                }
                if (NUMBER_ONLY.matcher(cleaned).matches()) {
                        return false;
                }
                return OPERATOR.matcher(cleaned).matches()
                                || PAREN.matcher(cleaned).matches()
                                || FUNCTION_CALL.matcher(cleaned).matches()
                                || VARIABLE_REFERENCE.matcher(cleaned).matches()
                                || UNIT_SUFFIX.matcher(cleaned).matches()
                                || RADIX_LITERAL.matcher(cleaned).matches();
        }

        /**
         * Per-character syntax colors for the search bar's highlighter:
         * one ARGB per character of the input, or {@code null} when the
         * input cannot even be tokenized (then the field stays plain).
         * The palette is NEC's: dark-aqua numbers, purple radix literals,
         * aqua units and constants, yellow functions, red operators, gray
         * delimiters, purple unknown words, gold dollar signs.
         */
        public static int[] syntaxColors(String input) {
                if (input == null || input.isEmpty()) {
                        return null;
                }
                try {
                        List<Token> tokens = tokenize(input);
                        int[] colors = new int[input.length()];
                        Arrays.fill(colors, SyntaxPalette.WORD);
                        for (Token token : tokens) {
                                if (token.is(Token.Kind.VARIABLE) && !token.text().isEmpty() && token.text().charAt(0) == '$') {
                                        colors[token.start()] = SyntaxPalette.DOLLAR;
                                        for (int i = token.start() + 1; i < token.end() && i < colors.length; i++) {
                                                colors[i] = SyntaxPalette.CUSTOM_VARIABLE;
                                        }
                                        continue;
                                }
                                int color = token.color();
                                for (int i = token.start(); i < token.end() && i < colors.length; i++) {
                                        colors[i] = color;
                                }
                        }
                        return colors;
                } catch (CalcException e) {
                        return null;
                }
        }

        /** Gate, parse, evaluate — the shared path of {@link #result} and {@link #compute}. */
        private static Optional<Node> evaluate(String input) {
                if (!looksLikeCalculation(input)) {
                        return Optional.empty();
                }
                try {
                        Node node = new Parser(tokenize(clean(input))).parseTop();
                        lastAnswer = node.value();
                        return Optional.of(node);
                } catch (CalcException e) {
                        return Optional.empty();
                }
        }

        /** NEC's cleanInput: strip format characters (BOM and friends), then trim. */
        private static String clean(String input) {
                return input == null ? "" : input.replaceAll("\\p{Cf}", "").trim();
        }

        // ------------------------------------------------------------------
        // Tokens
        // ------------------------------------------------------------------

        /** One lexical token with its source span (the highlighter reads the span). */
        private record Token(Kind kind, String text, BigDecimal number, Radix radix, int start) {
                private enum Kind {
                        NUMBER(SyntaxPalette.NUMBER),
                        UNIT(SyntaxPalette.UNIT),
                        PERCENT(SyntaxPalette.OPERATOR),
                        OPERATOR(SyntaxPalette.OPERATOR),
                        LEFT_PAREN(SyntaxPalette.DELIMITER),
                        RIGHT_PAREN(SyntaxPalette.DELIMITER),
                        COMMA(SyntaxPalette.DELIMITER),
                        ASSIGN(SyntaxPalette.OPERATOR),
                        FACTORIAL(SyntaxPalette.OPERATOR),
                        FUNCTION(SyntaxPalette.FUNCTION),
                        CONSTANT(SyntaxPalette.CONSTANT),
                        VARIABLE(SyntaxPalette.WORD);

                        private final int color;

                        Kind(int color) {
                                this.color = color;
                        }
                }

                Token(Kind kind, String text, int start) {
                        this(kind, text, null, Radix.NONE, start);
                }

                Token(Kind kind, String text, BigDecimal number, int start) {
                        this(kind, text, number, Radix.NONE, start);
                }

                private int color() {
                        if (kind == Kind.NUMBER && radix != Radix.NONE) {
                                return SyntaxPalette.RADIX_LITERAL; // 0x1F and friends
                        }
                        return kind.color;
                }

                private int end() {
                        return start + text.length();
                }

                private boolean is(Kind expected) {
                        return kind == expected;
                }

                private boolean isOperator(String symbol) {
                        return kind == Kind.OPERATOR && text.equals(symbol);
                }
        }

        /** The output radix a result carries (NEC's RadixMode). */
        private enum Radix {
                NONE, HEX, BIN, OCT, SHORTHAND
        }

        /** Internal failure: every path out of the engine is an empty Optional. */
        private static final class CalcException extends Exception {
        }

        /**
         * NEC's lexer: numbers (decimal and {@code 0b/0x/0o} with
         * underscores), units after numbers, glued-{@code %} percentage
         * versus spaced modulo, assignment, factorial, the operator set
         * with two-character shifts, {@code x}/{@code X} as multiplication
         * after a digit, a {@code )} or a unit, names (functions,
         * constants, variables), and an implicit-multiplication pass that
         * inserts {@code *} into {@code 2(}, {@code 2sqrt(}, {@code )(},
         * {@code )2} and after percentages and units.
         */
        private static List<Token> tokenize(String input) throws CalcException {
                List<Token> tokens = new ArrayList<>();
                int i = 0;
                int length = input.length();
                while (i < length) {
                        char c = input.charAt(i);
                        if (Character.isWhitespace(c)) {
                                i++;
                                continue;
                        }
                        // Radix literals: 0b1010, 0x1F, 0o17 (underscores allowed).
                        if (c == '0' && i + 1 < length) {
                                int radix = switch (input.charAt(i + 1)) {
                                        case 'b', 'B' -> 2;
                                        case 'x', 'X' -> 16;
                                        case 'o', 'O' -> 8;
                                        default -> 0;
                                };
                                if (radix > 0) {
                                        int start = i;
                                        i += 2;
                                        StringBuilder digits = new StringBuilder();
                                        while (i < length) {
                                                char rc = input.charAt(i);
                                                if (rc == '_') {
                                                        i++;
                                                        continue;
                                                }
                                                if (Character.digit(rc, radix) == -1) {
                                                        break;
                                                }
                                                digits.append(rc);
                                                i++;
                                        }
                                        if (digits.isEmpty()) {
                                                throw new CalcException();
                                        }
                                        BigInteger value;
                                        try {
                                                value = new BigInteger(digits.toString(), radix);
                                        } catch (NumberFormatException e) {
                                                throw new CalcException();
                                        }
                                        tokens.add(new Token(Token.Kind.NUMBER, input.substring(start, i),
                                                        new BigDecimal(value),
                                                        radix == 16 ? Radix.HEX : radix == 2 ? Radix.BIN : Radix.OCT, start));
                                        continue;
                                }
                        }
                        if (Character.isDigit(c) || c == '.') {
                                int start = i;
                                boolean dot = false;
                                while (i < length && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                                        if (input.charAt(i) == '.') {
                                                if (dot) {
                                                        throw new CalcException();
                                                }
                                                dot = true;
                                        }
                                        i++;
                                }
                                String text = input.substring(start, i);
                                if (text.equals(".")) {
                                        throw new CalcException();
                                }
                                try {
                                        tokens.add(new Token(Token.Kind.NUMBER, text, new BigDecimal(text), start));
                                } catch (NumberFormatException e) {
                                        throw new CalcException();
                                }
                                continue;
                        }
                        if (c == ',') {
                                tokens.add(new Token(Token.Kind.COMMA, ",", i));
                                i++;
                                continue;
                        }
                        if (c == '%') {
                                // NEC's disambiguation: % glued to a number is a
                                // percentage (50%), anything else is modulo.
                                boolean percentage = !tokens.isEmpty()
                                                && tokens.getLast().is(Token.Kind.NUMBER)
                                                && tokens.getLast().end() == i;
                                tokens.add(new Token(percentage ? Token.Kind.PERCENT : Token.Kind.OPERATOR, "%", i));
                                i++;
                                continue;
                        }
                        if (c == '=') {
                                tokens.add(new Token(Token.Kind.ASSIGN, "=", i));
                                i++;
                                continue;
                        }
                        if (c == '!') {
                                tokens.add(new Token(Token.Kind.FACTORIAL, "!", i));
                                i++;
                                continue;
                        }
                        if (c == '&' || c == '|' || c == '~') {
                                tokens.add(new Token(Token.Kind.OPERATOR, String.valueOf(c), i));
                                i++;
                                continue;
                        }
                        if (c == '<' && i + 1 < length && input.charAt(i + 1) == '<') {
                                tokens.add(new Token(Token.Kind.OPERATOR, "<<", i));
                                i += 2;
                                continue;
                        }
                        if (c == '>' && i + 1 < length && input.charAt(i + 1) == '>') {
                                tokens.add(new Token(Token.Kind.OPERATOR, ">>", i));
                                i += 2;
                                continue;
                        }
                        if (c == '+' || c == '-' || c == '*' || c == '/' || c == '^') {
                                tokens.add(new Token(Token.Kind.OPERATOR, String.valueOf(c), i));
                                i++;
                                continue;
                        }
                        if (c == '(') {
                                tokens.add(new Token(Token.Kind.LEFT_PAREN, "(", i));
                                i++;
                                continue;
                        }
                        if (c == ')') {
                                tokens.add(new Token(Token.Kind.RIGHT_PAREN, ")", i));
                                i++;
                                continue;
                        }
                        // NEC's rule: x/X multiplies when it follows a digit, a
                        // closing parenthesis or a unit — 2x3 is 6, hex( is a name.
                        if (c == 'x' || c == 'X') {
                                if (i > 0 && (Character.isDigit(input.charAt(i - 1)) || input.charAt(i - 1) == ')'
                                                || (!tokens.isEmpty() && tokens.getLast().is(Token.Kind.UNIT)))) {
                                        tokens.add(new Token(Token.Kind.OPERATOR, "*", i));
                                        i++;
                                        continue;
                                }
                        }
                        if (c == '$' || Character.isLetter(c) || c == '_') {
                                int start = i;
                                StringBuilder name = new StringBuilder();
                                name.append(c);
                                i++;
                                while (i < length && (Character.isLetterOrDigit(input.charAt(i)) || input.charAt(i) == '_')) {
                                        name.append(input.charAt(i));
                                        i++;
                                }
                                String raw = name.toString();
                                String lower = raw.toLowerCase(Locale.ROOT);
                                if (FUNCTIONS.contains(lower)) {
                                        tokens.add(new Token(Token.Kind.FUNCTION, lower, start));
                                } else if (lower.equals("pi")) {
                                        tokens.add(new Token(Token.Kind.CONSTANT, raw, new BigDecimal("3.14159265358979323846"), start));
                                } else if (lower.equals("e")) {
                                        tokens.add(new Token(Token.Kind.CONSTANT, raw, new BigDecimal("2.71828182845904523536"), start));
                                } else if (lower.equals("ans")) {
                                        tokens.add(new Token(Token.Kind.CONSTANT, raw, lastAnswer, start));
                                } else if (UNITS.containsKey(lower)
                                                && !tokens.isEmpty() && tokens.getLast().is(Token.Kind.NUMBER)) {
                                        tokens.add(new Token(Token.Kind.UNIT, lower, start));
                                } else {
                                        tokens.add(new Token(Token.Kind.VARIABLE, raw, start));
                                }
                                continue;
                        }
                        throw new CalcException();
                }
                return insertImplicitMultiplication(tokens);
        }

        /** NEC's implicit-multiplication pass between the paired token kinds. */
        private static List<Token> insertImplicitMultiplication(List<Token> tokens) {
                List<Token> result = new ArrayList<>(tokens.size() + 4);
                for (int i = 0; i < tokens.size(); i++) {
                        Token current = tokens.get(i);
                        result.add(current);
                        if (i + 1 >= tokens.size()) {
                                continue;
                        }
                        Token next = tokens.get(i + 1);
                        boolean multiply = switch (current.kind) {
                                case NUMBER -> next.is(Token.Kind.LEFT_PAREN) || next.is(Token.Kind.FUNCTION);
                                case RIGHT_PAREN -> next.is(Token.Kind.NUMBER) || next.is(Token.Kind.LEFT_PAREN)
                                                || next.is(Token.Kind.FUNCTION);
                                case UNIT -> next.is(Token.Kind.LEFT_PAREN);
                                case PERCENT -> next.is(Token.Kind.NUMBER) || next.is(Token.Kind.LEFT_PAREN)
                                                || next.is(Token.Kind.FUNCTION);
                                default -> false;
                        };
                        if (multiply) {
                                result.add(new Token(Token.Kind.OPERATOR, "*", current.end()));
                        }
                }
                return result;
        }

        // ------------------------------------------------------------------
        // Parser (NEC's precedence chain, recursive descent)
        // ------------------------------------------------------------------

        /** A parsed subtree: its value, percentage flag, radix and next position. */
        private record Node(BigDecimal value, boolean percentage, Radix radix, int next) {
        }

        /**
         * The recursive-descent interpreter. Precedence, loosest to
         * tightest: assignment, {@code |}, {@code &}, shifts,
         * {@code + -} (percentage-aware), {@code * / %}, {@code ^}
         * (right-associative), unary {@code - + ~}, postfix {@code !},
         * primary.
         */
        private static final class Parser {
                private final List<Token> tokens;

                private Parser(List<Token> tokens) {
                        this.tokens = tokens;
                }

                /** Parses the whole token stream; leftover tokens are a failure. */
                private Node parseTop() throws CalcException {
                        Node node = parseExpression(0);
                        if (node.next() < tokens.size()) {
                                throw new CalcException();
                        }
                        return node;
                }

                private boolean atEnd(int pos) {
                        return pos >= tokens.size();
                }

                private Token at(int pos) {
                        return tokens.get(pos);
                }

                /** Expression: an assignment ($x = …) or the operator chain. */
                private Node parseExpression(int pos) throws CalcException {
                        if (!atEnd(pos) && at(pos).is(Token.Kind.VARIABLE)
                                        && !atEnd(pos + 1) && at(pos + 1).is(Token.Kind.ASSIGN)) {
                                String name = at(pos).text().toLowerCase(Locale.ROOT);
                                if (name.startsWith("$")) {
                                        name = name.substring(1);
                                }
                                if (RESERVED.contains(name)) {
                                        throw new CalcException();
                                }
                                Node right = parseBitwiseOr(pos + 2);
                                variables.put(name, right.value());
                                return right;
                        }
                        return parseBitwiseOr(pos);
                }

                private Node parseBitwiseOr(int pos) throws CalcException {
                        Node left = parseBitwiseAnd(pos);
                        int current = left.next();
                        while (!atEnd(current) && at(current).isOperator("|")) {
                                Node right = parseBitwiseAnd(current + 1);
                                left = new Node(new BigDecimal(longOf(left).or(longOf(right))),
                                                false, radixOf(left, right), right.next());
                                current = right.next();
                        }
                        return left;
                }

                private Node parseBitwiseAnd(int pos) throws CalcException {
                        Node left = parseShift(pos);
                        int current = left.next();
                        while (!atEnd(current) && at(current).isOperator("&")) {
                                Node right = parseShift(current + 1);
                                left = new Node(new BigDecimal(longOf(left).and(longOf(right))),
                                                false, radixOf(left, right), right.next());
                                current = right.next();
                        }
                        return left;
                }

                private Node parseShift(int pos) throws CalcException {
                        Node left = parseAddSub(pos);
                        int current = left.next();
                        while (!atEnd(current) && (at(current).isOperator("<<") || at(current).isOperator(">>"))) {
                                boolean leftShift = at(current).isOperator("<<");
                                Node right = parseAddSub(current + 1);
                                long a = longOf(left).longValue();
                                long b;
                                try {
                                        b = right.value().intValueExact();
                                } catch (ArithmeticException e) {
                                        throw new CalcException();
                                }
                                left = new Node(BigDecimal.valueOf(leftShift ? a << b : a >> b),
                                                false, radixOf(left, right), right.next());
                                current = right.next();
                        }
                        return left;
                }

                /**
                 * Addition and subtraction, NEC's percentage rule included:
                 * {@code 100+10%} is 110, because a percentage on the right
                 * of a sum applies to the left side.
                 */
                private Node parseAddSub(int pos) throws CalcException {
                        Node left = parseMulDiv(pos);
                        int current = left.next();
                        while (!atEnd(current) && (at(current).isOperator("+") || at(current).isOperator("-"))) {
                                boolean add = at(current).isOperator("+");
                                Node right = parseMulDiv(current + 1);
                                BigDecimal value;
                                if (right.percentage()) {
                                        BigDecimal percent = left.value().multiply(right.value(), MATH);
                                        value = add ? left.value().add(percent, MATH) : left.value().subtract(percent, MATH);
                                } else {
                                        value = add ? left.value().add(right.value(), MATH)
                                                        : left.value().subtract(right.value(), MATH);
                                }
                                left = new Node(value, false, radixOf(left, right), right.next());
                                current = right.next();
                        }
                        return left;
                }

                private Node parseMulDiv(int pos) throws CalcException {
                        Node left = parsePower(pos);
                        int current = left.next();
                        while (!atEnd(current) && (at(current).isOperator("*") || at(current).isOperator("/")
                                        || at(current).isOperator("%"))) {
                                String op = at(current).text();
                                Node right = parsePower(current + 1);
                                BigDecimal value = switch (op) {
                                        case "*" -> left.value().multiply(right.value(), MATH);
                                        case "/" -> right.value().signum() == 0 ? null
                                                        : left.value().divide(right.value(), MATH);
                                        default -> right.value().signum() == 0 ? null
                                                        : left.value().remainder(right.value(), MATH);
                                };
                                if (value == null) {
                                        throw new CalcException();
                                }
                                left = new Node(value, false, radixOf(left, right), right.next());
                                current = right.next();
                        }
                        return left;
                }

                /** Power is right-associative: 2^3^2 is 512. */
                private Node parsePower(int pos) throws CalcException {
                        Node left = parseUnary(pos);
                        if (!atEnd(left.next()) && at(left.next()).isOperator("^")) {
                                Node right = parsePower(left.next() + 1);
                                return new Node(power(left.value(), right.value()), false,
                                                left.radix() != Radix.NONE ? left.radix() : right.radix(), right.next());
                        }
                        return left;
                }

                private Node parseUnary(int pos) throws CalcException {
                        if (atEnd(pos)) {
                                throw new CalcException();
                        }
                        if (at(pos).isOperator("-")) {
                                Node inner = parseUnary(pos + 1);
                                return new Node(inner.value().negate(), inner.percentage(), inner.radix(), inner.next());
                        }
                        if (at(pos).isOperator("+")) {
                                return parseUnary(pos + 1);
                        }
                        if (at(pos).isOperator("~")) {
                                Node inner = parseUnary(pos + 1);
                                return new Node(BigDecimal.valueOf(~longOf(inner).longValue()), false,
                                                inner.radix(), inner.next());
                        }
                        return parseFactorial(pos);
                }

                private Node parseFactorial(int pos) throws CalcException {
                        Node left = parsePrimary(pos);
                        int current = left.next();
                        while (!atEnd(current) && at(current).is(Token.Kind.FACTORIAL)) {
                                long n = longOf(left).longValue();
                                if (n < 0 || n > MAX_FACTORIAL) {
                                        throw new CalcException();
                                }
                                BigDecimal factorial = BigDecimal.ONE;
                                for (long i = 2; i <= n; i++) {
                                        factorial = factorial.multiply(BigDecimal.valueOf(i));
                                }
                                left = new Node(factorial, false, left.radix(), current + 1);
                                current++;
                        }
                        return left;
                }

                private Node parsePrimary(int pos) throws CalcException {
                        if (atEnd(pos)) {
                                throw new CalcException();
                        }
                        Token token = at(pos);

                        if (token.is(Token.Kind.NUMBER)) {
                                BigDecimal value = token.number();
                                Radix radix = token.radix();
                                int next = pos + 1;
                                if (!atEnd(next) && at(next).is(Token.Kind.UNIT)) {
                                        value = value.multiply(UNITS.get(at(next).text()));
                                        next++;
                                }
                                boolean percentage = false;
                                if (!atEnd(next) && at(next).is(Token.Kind.PERCENT)) {
                                        value = value.divide(new BigDecimal("100"), MATH);
                                        percentage = true;
                                        next++;
                                }
                                return new Node(value, percentage, radix, next);
                        }

                        if (token.is(Token.Kind.CONSTANT)) {
                                return new Node(token.number(), false, Radix.NONE, pos + 1);
                        }

                        if (token.is(Token.Kind.VARIABLE)) {
                                String name = token.text().toLowerCase(Locale.ROOT);
                                if (name.startsWith("$")) {
                                        name = name.substring(1);
                                }
                                BigDecimal value = variables.get(name);
                                if (value == null) {
                                        throw new CalcException(); // undefined variable
                                }
                                return new Node(value, false, Radix.NONE, pos + 1);
                        }

                        if (token.is(Token.Kind.FUNCTION)) {
                                return parseFunction(token, pos);
                        }

                        if (token.is(Token.Kind.LEFT_PAREN)) {
                                Node inner = parseExpression(pos + 1);
                                if (atEnd(inner.next()) || !at(inner.next()).is(Token.Kind.RIGHT_PAREN)) {
                                        throw new CalcException();
                                }
                                int next = inner.next() + 1;
                                BigDecimal value = inner.value();
                                boolean percentage = false;
                                if (!atEnd(next) && at(next).is(Token.Kind.PERCENT)) {
                                        value = value.divide(new BigDecimal("100"), MATH);
                                        percentage = true;
                                        next++;
                                }
                                return new Node(value, percentage, inner.radix(), next);
                        }

                        throw new CalcException();
                }

                private Node parseFunction(Token token, int pos) throws CalcException {
                        if (atEnd(pos + 1) || !at(pos + 1).is(Token.Kind.LEFT_PAREN)) {
                                throw new CalcException();
                        }
                        String name = token.text();
                        if (name.equals("clamp")) {
                                Args args = parseArguments(pos + 2, 3);
                                return new Node(args.get(0).value().min(args.get(2).value()).max(args.get(1).value()),
                                                false, args.get(0).radix(), args.next());
                        }
                        if (name.equals("min") || name.equals("max") || name.equals("gcd") || name.equals("lcm")
                                        || name.equals("xor") || name.equals("pct")) {
                                Args args = parseArguments(pos + 2, 2);
                                return new Node(binaryFunction(name, args.get(0).value(), args.get(1).value()),
                                                false, args.get(0).radix(), args.next());
                        }
                        if (name.equals("avg")) {
                                Args args = parseArguments(pos + 2, -1);
                                BigDecimal sum = BigDecimal.ZERO;
                                for (Node arg : args.nodes()) {
                                        sum = sum.add(arg.value());
                                }
                                BigDecimal value = args.nodes().isEmpty() ? BigDecimal.ZERO
                                                : sum.divide(BigDecimal.valueOf(args.nodes().size()), MATH);
                                return new Node(value, false, args.nodes().isEmpty() ? Radix.NONE
                                                : args.get(0).radix(), args.next());
                        }
                        // The one-argument functions.
                        Node arg = parseExpression(pos + 2);
                        if (atEnd(arg.next()) || !at(arg.next()).is(Token.Kind.RIGHT_PAREN)) {
                                throw new CalcException();
                        }
                        Radix radix = switch (name) {
                                case "hex" -> Radix.HEX;
                                case "bin" -> Radix.BIN;
                                case "oct" -> Radix.OCT;
                                case "fmt" -> Radix.SHORTHAND;
                                default -> arg.radix();
                        };
                        return new Node(unaryFunction(name, arg.value()), false, radix, arg.next() + 1);
                }

                /**
                 * Parses comma-separated arguments: exactly {@code count}
                 * (comma required between them), or any number when count is
                 * negative (zero allowed for {@code avg}).
                 */
                private Args parseArguments(int pos, int count) throws CalcException {
                        List<Node> nodes = new ArrayList<>();
                        int current = pos;
                        int index = 0;
                        while (true) {
                                if (count >= 0 && index == count) {
                                        break;
                                }
                                if (!atEnd(current) && at(current).is(Token.Kind.RIGHT_PAREN) && count < 0) {
                                        break; // avg() — no arguments at all
                                }
                                Node arg = parseExpression(current);
                                nodes.add(arg);
                                current = arg.next();
                                index++;
                                if (!atEnd(current) && at(current).is(Token.Kind.COMMA)) {
                                        current++;
                                        continue;
                                }
                                break;
                        }
                        if (atEnd(current) || !at(current).is(Token.Kind.RIGHT_PAREN)) {
                                throw new CalcException();
                        }
                        return new Args(nodes, current + 1);
                }

                /** Parsed arguments plus the position after the closing parenthesis. */
                private record Args(List<Node> nodes, int next) {
                        private Node get(int index) {
                                return nodes.get(index);
                        }
                }

                private BigInteger longOf(Node node) throws CalcException {
                        try {
                                return node.value().toBigIntegerExact();
                        } catch (ArithmeticException e) {
                                throw new CalcException();
                        }
                }

                private Radix radixOf(Node left, Node right) {
                        return right.radix() != Radix.NONE ? right.radix() : left.radix();
                }

                private BigDecimal binaryFunction(String name, BigDecimal a, BigDecimal b) throws CalcException {
                        try {
                                return switch (name) {
                                        case "min" -> a.min(b);
                                        case "max" -> a.max(b);
                                        case "gcd" -> new BigDecimal(a.toBigInteger().gcd(b.toBigInteger()));
                                        case "lcm" -> {
                                                BigInteger x = a.toBigInteger();
                                                BigInteger y = b.toBigInteger();
                                                if (x.signum() == 0 || y.signum() == 0) {
                                                        yield BigDecimal.ZERO;
                                                }
                                                yield new BigDecimal(x.multiply(y).abs().divide(x.gcd(y)));
                                        }
                                        case "xor" -> BigDecimal.valueOf(a.longValueExact() ^ b.longValueExact());
                                        case "pct" -> a.multiply(new BigDecimal("100")).divide(b, MATH);
                                        default -> throw new CalcException();
                                };
                        } catch (ArithmeticException e) {
                                throw new CalcException();
                        }
                }

                private BigDecimal unaryFunction(String name, BigDecimal arg) throws CalcException {
                        try {
                                return switch (name) {
                                        case "sqrt" -> arg.signum() < 0 ? fail() : arg.sqrt(MATH);
                                        case "abs" -> arg.abs();
                                        case "floor" -> arg.setScale(0, RoundingMode.FLOOR);
                                        case "ceil" -> arg.setScale(0, RoundingMode.CEILING);
                                        case "round" -> arg.setScale(0, RoundingMode.HALF_UP);
                                        case "log" -> arg.signum() <= 0 ? fail() : BigDecimal.valueOf(Math.log10(arg.doubleValue()));
                                        case "ln" -> arg.signum() <= 0 ? fail() : BigDecimal.valueOf(Math.log(arg.doubleValue()));
                                        case "sin" -> BigDecimal.valueOf(Math.sin(Math.toRadians(arg.doubleValue())));
                                        case "cos" -> BigDecimal.valueOf(Math.cos(Math.toRadians(arg.doubleValue())));
                                        case "tan" -> BigDecimal.valueOf(Math.tan(Math.toRadians(arg.doubleValue())));
                                        case "rad" -> BigDecimal.valueOf(Math.toRadians(arg.doubleValue()));
                                        case "deg" -> BigDecimal.valueOf(Math.toDegrees(arg.doubleValue()));
                                        // hex/bin/oct/fmt only set the output radix; the
                                        // value passes through untouched.
                                        case "hex", "bin", "oct", "fmt" -> arg;
                                        default -> throw new CalcException();
                                };
                        } catch (ArithmeticException e) {
                                throw new CalcException();
                        }
                }

                /** NEC's power: integer exponents in BigDecimal, the rest through double. */
                private BigDecimal power(BigDecimal base, BigDecimal exponent) throws CalcException {
                        try {
                                int exp = exponent.intValueExact();
                                if (exp < -MAX_EXPONENT || exp > MAX_EXPONENT) {
                                        throw new CalcException();
                                }
                                return base.pow(exp, MATH);
                        } catch (ArithmeticException e) {
                                if (base.signum() < 0) {
                                        throw new CalcException(); // negative base, fractional exponent
                                }
                                return BigDecimal.valueOf(Math.pow(base.doubleValue(), exponent.doubleValue()));
                        }
                }

                private BigDecimal fail() throws CalcException {
                        throw new CalcException();
                }
        }

        // ------------------------------------------------------------------
        // Formatting
        // ------------------------------------------------------------------

        /** The result as shown: radix-shaped (0x…, 0b…, 0o…, shorthand) or grouped decimal. */
        private static String formatNode(Node node) {
                return switch (node.radix()) {
                        case HEX -> formatWithRadix(node.value(), "0x", 16, true);
                        case BIN -> formatWithRadix(node.value(), "0b", 2, false);
                        case OCT -> formatWithRadix(node.value(), "0o", 8, false);
                        case SHORTHAND -> toShorthand(node.value());
                        case NONE -> format(node.value());
                };
        }

        /** NEC's radix formatting: 0xFF uppercase hex, plain binary/octal, sign carried. */
        private static String formatWithRadix(BigDecimal value, String prefix, int radix, boolean uppercase) {
                try {
                        BigInteger integer = value.toBigInteger();
                        String digits = integer.abs().toString(radix);
                        if (uppercase) {
                                digits = digits.toUpperCase(Locale.ROOT);
                        }
                        return (integer.signum() < 0 ? "-" : "") + prefix + digits;
                } catch (ArithmeticException e) {
                        return format(value); // fractional value: no radix shape
                }
        }

        /** NEC's shorthand: 1500000 shows as 1.5m (t/b/m/k, four fraction digits). */
        private static String toShorthand(BigDecimal value) {
                BigDecimal abs = value.abs();
                for (int i = 0; i < SHORTHAND_UNITS.length; i++) {
                        BigDecimal unit = SHORTHAND_UNITS[i];
                        if (abs.compareTo(unit) >= 0) {
                                BigDecimal scaled = value.divide(unit, 4, RoundingMode.HALF_UP).stripTrailingZeros();
                                return scaled.toPlainString() + SHORTHAND_SUFFIXES[i];
                        }
                }
                return format(value);
        }

        private static final BigDecimal[] SHORTHAND_UNITS = {
                        new BigDecimal("1000000000000"), new BigDecimal("1000000000"),
                        new BigDecimal("1000000"), new BigDecimal("1000")};

        private static final String[] SHORTHAND_SUFFIXES = {"t", "b", "m", "k"};

        /**
         * NEC's comma inserter: pure string work on the plain string, so
         * unlimited digit counts group correctly (DecimalFormat caps out).
         */
        private static String insertCommas(String number) {
                if (number == null || number.isEmpty()) {
                        return "";
                }
                int dot = number.indexOf('.');
                String whole = dot >= 0 ? number.substring(0, dot) : number;
                String fraction = dot >= 0 ? number.substring(dot) : "";
                String sign = "";
                if (whole.startsWith("-") || whole.startsWith("+")) {
                        sign = whole.substring(0, 1);
                        whole = whole.substring(1);
                }
                StringBuilder grouped = new StringBuilder(sign);
                int length = whole.length();
                for (int i = 0; i < length; i++) {
                        if (i > 0 && (length - i) % 3 == 0) {
                                grouped.append(',');
                        }
                        grouped.append(whole.charAt(i));
                }
                return grouped + fraction;
        }

        // ------------------------------------------------------------------
        // Syntax palette (NEC's ColorConstants as ARGB)
        // ------------------------------------------------------------------

        /** One ARGB color per token class; NEC's §-codes translated. */
        static final class SyntaxPalette {
                /** §3 dark aqua — numbers. */
                static final int NUMBER = 0xFF00AAAA;
                /** §d light purple — radix literals and unknown words. */
                static final int RADIX_LITERAL = 0xFFFF55FF;
                /** §b aqua — units and built-in constants. */
                static final int UNIT = 0xFF55FFFF;
                /** §e yellow — functions. */
                static final int FUNCTION = 0xFFFFFF55;
                /** §b aqua — pi, e, ans. */
                static final int CONSTANT = 0xFF55FFFF;
                /** §3 dark aqua — $ variable names. */
                static final int CUSTOM_VARIABLE = 0xFF00AAAA;
                /** §6 gold — the dollar sign itself. */
                static final int DOLLAR = 0xFFFFAA00;
                /** §d light purple — words that are nothing else. */
                static final int WORD = 0xFFFF55FF;
                /** §c red — operators, %, !, =. */
                static final int OPERATOR = 0xFFFF5555;
                /** §7 gray — parentheses and commas. */
                static final int DELIMITER = 0xFFAAAAAA;

                private SyntaxPalette() {
                }
        }
}
