package com.example.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Calculator Tests")
class CalculatorTest {

    private Calculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new Calculator();
    }

    @Nested
    @DisplayName("Addition Tests")
    class AdditionTests {

        @Test
        @DisplayName("Should return correct sum of two positive numbers")
        void testAddPositiveNumbers() {
            int result = calculator.add(10, 20);
            assertEquals(30, result, "10 + 20 should equal 30");
        }

        @ParameterizedTest(name = "{0} + {1} = {2}")
        @CsvSource({
                "1, 1, 2",
                "5, 3, 8",
                "-1, -1, -2",
                "-5, 10, 5",
                "0, 0, 0"
        })
        @DisplayName("Parameterized test for addition")
        void parameterizedTestForAddition(int a, int b, int expected){
            assertEquals(expected, calculator.add(a, b));
        }

    }

    @Nested
    @DisplayName("Subtraction Tests")
    class SubtractionTests {

        @Test
        @DisplayName("Should subtract two numbers correctly")
        void testSubtract() {
            assertEquals(5, calculator.subtract(10, 5));
        }

        @Test
        @DisplayName("Should subtract two negative numbers correctly")
        void testSubtractNegativeNumbers() {
            assertEquals(-10, calculator.subtract(-5, 5));
        }
    }

    @Nested
    @DisplayName("Multiplication Tests")
    class MultiplicationTests {

        @Test
        @DisplayName("Should multiply two numbers correctly")
        void testMultiply() {
            assertAll("Multiplication assertions",
                    () -> assertEquals(20, calculator.multiply(4, 5)),
                    () -> assertEquals(0, calculator.multiply(4, 0)),
                    () -> assertEquals(-12, calculator.multiply(3, -4))
            );
        }

        @ParameterizedTest(name = "{0} * {1} = {2}")
        @CsvSource({
                "1, 1, 1",
                "5, 3, 15",
                "-1, -1, 1",
                "-5, 10, -50",
                "0, 0, 0"
        })
        void parametrizedMultiplcation(int a, int b, int expected){
            assertEquals(expected, calculator.multiply(a, b));
        }
    }


    @Nested
    @DisplayName("Division Tests")
    class DivisionTests {

        @Test
        @DisplayName("Should divide two numbers correctly")
        void testDivide() {
            assertEquals(2.5, calculator.divide(5, 2), 0.0001);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when dividing by zero")
        void testDivideByZero() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, ()-> calculator.divide(4, 0));
            assertEquals("Cannot divide by zero", ex.getMessage());
        }
    }
}