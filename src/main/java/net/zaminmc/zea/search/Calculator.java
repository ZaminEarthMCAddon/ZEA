package net.zaminmc.zea.search;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A small, safe arithmetic parser for the inventory search bar: numbers,
 * {@code + - * /}, parentheses and unary minus, evaluated with
 * {@link BigDecimal} arithmetic. It is a hand-written lexer plus
 * shunting-yard plus an RPN walk — the same shape as NEU's calculator,
 * minus its price variables — and deliberately nothing more: no method
 * calls, no exponentiation, no reflection, no eval. Whatever it does not
 * fully understand returns empty, so an unfinished or invalid expression
 * simply shows no result instead of a wrong one.
 *
 * <p>Division runs in a fixed precision context (16 significant digits),
 * so {@code 1/3} resolves instead of blowing up; everything else is exact
 * BigDecimal arithmetic, immune to double rounding surprises.
 */
public final class Calculator {
        /** The context division evaluates in; matches double precision without double rounding. */
        private static final MathContext DIVISION = MathContext.DECIMAL64;

        /** Input longer than this is refused outright — the edit box caps input at 64 anyway. */
        public static final int MAX_INPUT_LENGTH = 64;

        private Calculator() {
        }

        /** One lexical token: a number, an operator or a parenthesis. */
        private sealed interface Token {
                record Number(BigDecimal value) implements Token {
                }

                /** Binary or unary operator, by its character. */
                record Operator(char symbol, boolean unary) implements Token {
                }

                record LeftParen() implements Token {
                }

                record RightParen() implements Token {
                }
        }

        /** Turns input into tokens; returns empty on any character it does not understand. */
        private static Optional<List<Token>> lex(String input) {
                if (input == null || input.isBlank() || input.length() > MAX_INPUT_LENGTH) {
                        return Optional.empty();
                }
                List<Token> tokens = new ArrayList<>();
                int i = 0;
                int length = input.length();
                while (i < length) {
                        char c = input.charAt(i);
                        if (Character.isWhitespace(c)) {
                                i++;
                                continue;
                        }
                        if (c == '(') {
                                tokens.add(new Token.LeftParen());
                                i++;
                                continue;
                        }
                        if (c == ')') {
                                tokens.add(new Token.RightParen());
                                i++;
                                continue;
                        }
                        if (c == '+' || c == '-' || c == '*' || c == '/') {
                                boolean unary = c == '-'
                                                && (tokens.isEmpty()
                                                                || tokens.getLast() instanceof Token.Operator
                                                                || tokens.getLast() instanceof Token.LeftParen);
                                tokens.add(new Token.Operator(c, unary));
                                i++;
                                continue;
                        }
                        if (Character.isDigit(c) || c == '.') {
                                int start = i;
                                int dots = 0;
                                while (i < length && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                                        if (input.charAt(i) == '.') {
                                                dots++;
                                        }
                                        i++;
                                }
                                String literal = input.substring(start, i);
                                if (dots > 1 || literal.equals(".")) {
                                        return Optional.empty();
                                }
                                try {
                                        tokens.add(new Token.Number(new BigDecimal(literal)));
                                } catch (NumberFormatException e) {
                                        return Optional.empty();
                                }
                                continue;
                        }
                        // Any other character: not arithmetic, not our problem.
                        return Optional.empty();
                }
                return Optional.of(tokens);
        }

        /** Operator precedence: unary minus binds tightest, then * /, then + -. */
        private static int precedence(Token.Operator operator) {
                if (operator.unary()) {
                        return 3;
                }
                return operator.symbol() == '*' || operator.symbol() == '/' ? 2 : 1;
        }

        /** Shunting-yard: infix tokens to reverse polish notation. */
        private static Optional<List<Token>> toRpn(List<Token> tokens) {
                List<Token> output = new ArrayList<>(tokens.size());
                Deque<Token> stack = new ArrayDeque<>();
                for (Token token : tokens) {
                        switch (token) {
                                case Token.Number number -> output.add(number);
                                case Token.LeftParen left -> stack.push(left);
                                case Token.RightParen right -> {
                                        while (!stack.isEmpty() && !(stack.peek() instanceof Token.LeftParen)) {
                                                output.add(stack.pop());
                                        }
                                        if (stack.isEmpty()) {
                                                return Optional.empty(); // unbalanced
                                        }
                                        stack.pop();
                                }
                                case Token.Operator operator -> {
                                        while (!stack.isEmpty() && stack.peek() instanceof Token.Operator top) {
                                                boolean leftAssociative = !operator.unary();
                                                if (leftAssociative
                                                                ? precedence(operator) <= precedence(top)
                                                                : precedence(operator) < precedence(top)) {
                                                        output.add(stack.pop());
                                                } else {
                                                        break;
                                                }
                                        }
                                        stack.push(operator);
                                }
                        }
                }
                while (!stack.isEmpty()) {
                        Token top = stack.pop();
                        if (top instanceof Token.LeftParen) {
                                return Optional.empty(); // unbalanced
                        }
                        output.add(top);
                }
                return Optional.of(output);
        }

        /** Walks the RPN; empty on stack underflow, a stray token or division by zero. */
        private static Optional<BigDecimal> evaluate(List<Token> rpn) {
                Deque<BigDecimal> stack = new ArrayDeque<>();
                for (Token token : rpn) {
                        switch (token) {
                                case Token.Number number -> stack.push(number.value());
                                case Token.Operator operator -> {
                                        if (operator.unary()) {
                                                BigDecimal value = stack.poll();
                                                if (value == null) {
                                                        return Optional.empty();
                                                }
                                                stack.push(value.negate());
                                                continue;
                                        }
                                        BigDecimal right = stack.poll();
                                        BigDecimal left = stack.poll();
                                        if (right == null || left == null) {
                                                return Optional.empty();
                                        }
                                        BigDecimal result = switch (operator.symbol()) {
                                                case '+' -> left.add(right);
                                                case '-' -> left.subtract(right);
                                                case '*' -> left.multiply(right);
                                                case '/' -> right.signum() == 0
                                                                ? null
                                                                : left.divide(right, DIVISION);
                                                default -> null;
                                        };
                                        if (result == null) {
                                                return Optional.empty();
                                        }
                                        stack.push(result);
                                }
                                default -> {
                                        return Optional.empty();
                                }
                        }
                }
                if (stack.size() != 1) {
                        return Optional.empty();
                }
                return Optional.of(stack.pop());
        }

        /**
         * Evaluates the expression. Empty when the input is not a complete,
         * valid arithmetic expression — the caller shows nothing rather than
         * a guess.
         */
        public static Optional<BigDecimal> compute(String input) {
                return lex(input).flatMap(Calculator::toRpn).flatMap(Calculator::evaluate);
        }

        /**
         * The result as the search bar shows it: grouped digits, at most
         * ten fraction digits, no trailing zeros, never scientific notation.
         */
        public static String format(BigDecimal value) {
                BigDecimal stripped = value.stripTrailingZeros();
                if (stripped.scale() < 0) {
                        stripped = stripped.setScale(0);
                }
                DecimalFormat format = new DecimalFormat("#,##0.##########", new DecimalFormatSymbols(Locale.ROOT));
                return format.format(stripped);
        }
}
