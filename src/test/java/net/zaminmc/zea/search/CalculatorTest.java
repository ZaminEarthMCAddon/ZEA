package net.zaminmc.zea.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calculator on NotEnoughCalculator's grammar: every NEC feature
 * ZEA ports, plus the two behaviors this rebuild was for — a bare
 * number shows nothing, and an unfinished expression shows nothing.
 */
class CalculatorTest {
	@BeforeEach
	void freshSession() {
		Calculator.resetSession();
	}

	private static Optional<BigDecimal> compute(String input) {
		return Calculator.compute(input);
	}

	private static String result(String input) {
		return Calculator.result(input).orElse(null);
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

	// ------------------------------------------------------------------
	// The NEC gate: bare numbers and plain words show nothing
	// ------------------------------------------------------------------

	@Test
	void plainNumbersShowNoResult() {
		// NEC: "Single number (e.g. 64) is usually an item count
		// search, not math" — the complaint this rebuild fixes.
		assertNullResult("2");
		assertNullResult("42");
		assertNullResult(" 42 ");
		assertNullResult("2.5");
		assertNullResult("42.");
	}

	@Test
	void plainWordsShowNoResult() {
		assertNullResult("hello");
		assertNullResult("spawn");
		assertNullResult("diamond sword");
		assertNullResult("2 apples");
		assertNullResult("1;2");
		assertNullResult("1 + a");
	}

	@Test
	void divisionByZeroHasNoResult() {
		assertTrue(compute("1 / 0").isEmpty());
		assertTrue(compute("5 / (2 - 2)").isEmpty());
		assertTrue(compute("5 % 0").isEmpty());
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
		assertTrue(compute("sqrt(").isEmpty());
		assertTrue(compute("min(1").isEmpty());
	}

	@Test
	void malformedNumbersHaveNoResult() {
		assertTrue(compute("1..5").isEmpty());
		assertTrue(compute(".").isEmpty());
		assertTrue(compute("0x").isEmpty());
	}

	@Test
	void absurdlyLongInputIsRefused() {
		assertTrue(compute("1" + "+1".repeat(60)).isEmpty(),
				"input beyond the cap is not arithmetic — the edit box caps it anyway");
	}

	// ------------------------------------------------------------------
	// NEC's operators: power, factorial, modulo, percentages, bitwise
	// ------------------------------------------------------------------

	@Test
	void powerIsRightAssociative() {
		assertEquals("1,024", result("2^10"));
		assertEquals("512", result("2^3^2"));
		assertEquals("0.25", result("2^-2"));
		assertEquals("1.4142135624", result("2^0.5"));
	}

	@Test
	void factorial() {
		assertEquals("120", result("5!"));
		assertEquals("1", result("0!"));
		assertEquals("3,628,800", result("10!"));
		assertTrue(compute("(-1)!").isEmpty());
	}

	@Test
	void moduloNeedsSpacePercentGluesInstead() {
		// NEC: % glued to a number is a percentage; spaced % is modulo.
		assertEquals("1", result("10 % 3"));
		assertEquals("0.3", result("10%3")); // 10% of… times 3
	}

	@Test
	void percentagesApplyToTheLeftSideOfASum() {
		assertEquals("110", result("100+10%"));
		assertEquals("90", result("100-10%"));
		assertEquals("0.5", result("50%"));
		assertEquals("5", result("50*10%"));
		// NEC quirk: % after a closing parenthesis is modulo, not a
		// percentage — and a trailing modulo makes the expression
		// incomplete, so (1+1)% shows nothing.
		assertNullResult("(1+1)%");
	}

	@Test
	void bitwiseOperators() {
		assertEquals("2", result("6&3"));
		assertEquals("7", result("6|3"));
		assertEquals("5", result("xor(6, 3)"));
		assertEquals("-6", result("~5"));
		assertEquals("16", result("1<<4"));
		assertEquals("16", result("64>>2"));
	}

	@Test
	void theLetterXMultipliesLikeNec() {
		assertEquals("6", result("2x3"));
		assertEquals("6", result("2X3"));
		// But a bare x is a word, not an operator.
		assertTrue(compute("x").isEmpty());
		assertTrue(compute("2 x 3").isEmpty());
	}

	@Test
	void implicitMultiplication() {
		assertEquals("14", result("2(3+4)"));
		assertEquals("6", result("(2)(3)"));
		assertEquals("4", result("2sqrt(4)"));
		assertEquals("10", result("2 sqrt(4)+6"));
	}

	// ------------------------------------------------------------------
	// Units, constants, radix literals
	// ------------------------------------------------------------------

	@Test
	void coinAndStackUnits() {
		assertEquals("2,000", result("2k"));
		assertEquals("1,500,000", result("1.5m"));
		assertEquals("3,000,000,000", result("3b"));
		assertEquals("2,001", result("2k + 1"));
		assertEquals("640", result("10 stacks"));
		assertEquals("320", result("5s"));
		assertEquals("320", result("5st"));
		assertEquals("320", result("5stack"));
	}

	@Test
	void constants() {
		assertEquals("3.1415926536", result("pi"));
		assertEquals("2.7182818285", result("e"));
		assertEquals("6.2831853072", result("pi*2"));
	}

	@Test
	void radixLiteralsAndRadixFunctions() {
		// NEC: a radix literal shapes the result too — 0x1F shows 0x1F.
		assertEquals("0x1F", result("0x1F"));
		assertEquals("0b101", result("0b101"));
		assertEquals("0o17", result("0o17"));
		// The radix of the right operand carries to the result, like NEC.
		assertEquals("0x3E8", result("0b10 + 0x3E6"));
		assertEquals("0xFF", result("hex(255)"));
		assertEquals("0b101", result("bin(5)"));
		assertEquals("0o17", result("oct(15)"));
		assertEquals("0x20", result("0x1F + 1")); // radix carries
	}

	@Test
	void shorthandResults() {
		assertEquals("1.5m", result("fmt(1500000)"));
		assertEquals("1.5m", result("fmt(1.5m)"));
		assertEquals("2k", result("fmt(2000)"));
		assertEquals("500", result("fmt(500)"));
	}

	// ------------------------------------------------------------------
	// Functions
	// ------------------------------------------------------------------

	@Test
	void oneArgumentFunctions() {
		assertEquals("3", result("sqrt(9)"));
		assertEquals("3", result("abs(-3)"));
		assertEquals("3", result("floor(3.7)"));
		assertEquals("4", result("ceil(3.2)"));
		assertEquals("4", result("round(3.5)"));
		assertEquals("2", result("log(100)"));
		assertEquals("1", result("ln(e)"));
		assertEquals("0.5", result("sin(30)"));
		assertEquals("0.7071067812", result("cos(45)"));
		assertEquals("1", result("tan(45)"));
		assertEquals("0.7853981634", result("rad(45)"));
		assertEquals("45", result("deg(pi/4)"));
		assertTrue(compute("sqrt(-1)").isEmpty());
		assertTrue(compute("log(0)").isEmpty());
	}

	@Test
	void functionNamesAreCaseInsensitive() {
		assertEquals("3", result("SQRT(9)"));
		assertEquals("3", result("Sqrt(9)"));
	}

	@Test
	void twoArgumentFunctions() {
		assertEquals("2", result("min(2, 3)"));
		assertEquals("3", result("max(2, 3)"));
		assertEquals("6", result("gcd(12, 18)"));
		assertEquals("36", result("lcm(12, 18)"));
		assertEquals("25", result("pct(50, 200)"));
	}

	@Test
	void clampAndAvg() {
		assertEquals("5", result("clamp(10, 1, 5)"));
		assertEquals("1", result("clamp(0, 1, 5)"));
		assertEquals("3", result("clamp(3, 1, 5)"));
		assertEquals("2", result("avg(1, 2, 3)"));
		assertEquals("0", result("avg()"));
	}

	// ------------------------------------------------------------------
	// ans and $ variables (session state)
	// ------------------------------------------------------------------

	@Test
	void ansRemembersTheLastResult() {
		assertEquals("4", result("2+2"));
		assertEquals("8", result("ans*2"));
		assertEquals("2", result("ans/4"));
	}

	@Test
	void dollarVariablesAssignAndReference() {
		assertEquals("5", result("$x = 5"));
		assertEquals("10", result("$x * 2"));
		assertEquals("25", result("$x ^ 2"));
		// Undefined variables show nothing.
		assertTrue(compute("$y + 1").isEmpty());
		// Reserved names refuse assignment.
		assertTrue(compute("$pi = 5").isEmpty());
		assertTrue(compute("$e = 5").isEmpty());
		assertTrue(compute("$ans = 5").isEmpty());
		assertTrue(compute("$sqrt = 5").isEmpty());
		assertTrue(compute("$k = 5").isEmpty());
	}

	@Test
	void variablesSurviveWithinASession() {
		result("$price = 2.5k");
		assertEquals("3,000", result("$price + 500"));
	}

	// ------------------------------------------------------------------
	// Formatting
	// ------------------------------------------------------------------

	@Test
	void longRepeatingDivisionsTerminate() {
		String third = result("1 / 3");
		assertTrue(third.startsWith("0.3333"), "1/3 = " + third);
		assertTrue(third.length() <= 12, "the format caps fraction digits: " + third);
	}

	@Test
	void bigValuesFormatWithGroupingAndNoScientificNotation() {
		assertEquals("1,000,000", result("1000 * 1000"));
		assertEquals("1,234.45", result("1234.4500 + 0"));
		assertEquals("0.5", result("0.500000 * 1"));
		assertEquals("100,000,000,000", result("10^11"));
	}

	@Test
	void longChainsEvaluate() {
		assertEquals("10", result("1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 1"));
		assertEquals("64", result("2 * 2 * 2 * 2 * 2 * 2"));
	}

	@Test
	void theGateItself() {
		assertTrue(Calculator.looksLikeCalculation("2+2"));
		assertTrue(Calculator.looksLikeCalculation("pi"));
		assertTrue(Calculator.looksLikeCalculation("ans"));
		assertTrue(Calculator.looksLikeCalculation("0b1"));
		assertTrue(Calculator.looksLikeCalculation("sqrt(4)"));
		assertTrue(Calculator.looksLikeCalculation("2k"));
		assertTrue(Calculator.looksLikeCalculation("$x = 1"));
		assertFalse(Calculator.looksLikeCalculation("2"));
		assertFalse(Calculator.looksLikeCalculation(" 42 "));
		assertFalse(Calculator.looksLikeCalculation("diamond"));
		assertFalse(Calculator.looksLikeCalculation(""));
		assertFalse(Calculator.looksLikeCalculation(null));
	}

	@Test
	void syntaxColorsCoverTheWholeInput() {
		int[] colors = Calculator.syntaxColors("2+2");
		assertEquals(3, colors.length);
		colors = Calculator.syntaxColors("sqrt(2k) + $x");
		assertEquals(13, colors.length);
		// Untokenizable input yields no palette, and the field stays plain.
		assertNull(Calculator.syntaxColors("1;2"));
		assertNull(Calculator.syntaxColors(""));
		assertNull(Calculator.syntaxColors(null));
	}

	private static void assertNullResult(String input) {
		assertNull(result(input), "expected no result for '" + input + "'");
	}
}
