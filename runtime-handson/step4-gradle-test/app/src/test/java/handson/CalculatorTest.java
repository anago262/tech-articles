package handson;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {
    private final Calculator calc = new Calculator();

    @Test
    void addsTwoNumbers() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    void dividesTwoNumbers() {
        assertEquals(4, calc.divide(12, 3));
    }

    @Test
    void throwsOnDivisionByZero() {
        assertThrows(ArithmeticException.class, () -> calc.divide(1, 0));
    }
}
