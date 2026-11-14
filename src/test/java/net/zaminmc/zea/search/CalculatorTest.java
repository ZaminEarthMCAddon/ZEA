package net.zaminmc.zea.search;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorTest {
        private static Optional<BigDecimal> compute(String input) {
                return Calculator.compute(input);
        }

        private static String result(String input) {
                return compute(input).map(Calculator::format).orElse(null);
        }

        @Test
        void theSpecExamples() {
                assertEquals("43", result("39 + 4"));
                assertEquals("73", result("34 + 39"));
        }

        @Test
        void precedenceAndParentheses() {
                assertEquals("14", result("2 + 3 * 4"));
                assertEquals("20", result("(2 + 3) * 4"));
                assertEquals("9", result("(1+2)*3"));
                assertEquals("5", result("2 * 2 + 1"));
                assertEquals("4", result("2 + 4 / 2"));
        }

        @Test
        void subtractionMultiplicationDivision() {
                assertEquals("2", result("5 - 3"));
                assertEquals("-2", result("3 - 5"));
                assertEquals("12", result("3 * 4"));
                assertEquals("2.5", result("10 / 4"));
        }

        @Test
        void decimalsAndNegatives() {
                assertEquals("1.5", result("0.5 + 1"));
                assertEquals("0.75", result("1.5 / 2"));
                assertEquals("2", result("-2 * -1"));
                assertEquals("-5", result("-10 / 2"));
                assertEquals("3", result("-(-3)"));
                assertEquals("7", result("5--2"));
        }

        @Test
        void divisionByZeroHasNoResult() {
                assertTrue(compute("1 / 0").isEmpty());
                assertTrue(compute("5 / (2 - 2)").isEmpty());
        }

        @Test
        void incompleteExpressionsHaveNoResult() {
                assertTrue(compute("39 +").isEmpty());
                assertTrue(compute("+").isEmpty());
                assertTrue(compute("((").isEmpty());
                assertTrue(compute("1 + 2)").isEmpty());
                assertTrue(compute("").isEmpty());
                assertTrue(compute("   ").isEmpty());
                assertTrue(compute(null).isEmpty());
        }

        @Test
        void nonArithmeticInputHasNoResult() {
                assertTrue(compute("hello").isEmpty());
                assertTrue(compute("1 + a").isEmpty());
                assertTrue(compute("spawn").isEmpty());
                assertTrue(compute("1;2").isEmpty());
                // Letters are not arithmetic — even letters after digits.
                assertTrue(compute("2 apples").isEmpty());
        }

        @Test
        void malformedNumbersHaveNoResult() {
                assertTrue(compute("1..5").isEmpty());
                assertTrue(compute(".").isEmpty());
        }

        @Test
        void plainNumbersAreTheirOwnResult() {
                assertEquals("42", result("42"));
                assertEquals("42", result(" 42 "));
        }

        @Test
        void longRepeatingDivisionsTerminate() {
                String third = result("1 / 3");
                assertTrue(third.startsWith("0.3333"), "1/3 = " + third);
                assertTrue(third.length() <= 12, "the format caps fraction digits: " + third);
        }

        @Test
        void bigValuesFormatWithGroupingAndNoScientificNotation() {
                assertEquals("1,000,000", result("1000 * 1000"));
                assertEquals("123.45", result("123.4500"));
                assertEquals("0.5", result("0.500000"));
        }

        @Test
        void longChainsEvaluate() {
                assertEquals("10", result("1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1"));
                assertEquals("64", result("2 * 2 * 2 * 2 * 2 * 2"));
        }

        @Test
        void absurdlyLongInputIsRefused() {
                assertTrue(compute("1" + "+1".repeat(60)).isEmpty(),
                                "input beyond the cap is not arithmetic — the edit box caps it anyway");
        }
}
