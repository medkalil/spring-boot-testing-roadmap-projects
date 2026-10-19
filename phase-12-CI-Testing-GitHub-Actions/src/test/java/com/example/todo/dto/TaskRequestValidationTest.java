package com.example.todo.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.todo.entity.TaskStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Unit test (Surefire): bean validation on the request records, no Spring. */
class TaskRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validCreateRequest_hasNoViolations() {
        Set<ConstraintViolation<TaskCreateRequest>> violations =
                validator.validate(new TaskCreateRequest("Write tests", "with care", null));

        assertThat(violations).isEmpty();
    }

    @Test
    void blankTitle_isRejected() {
        Set<ConstraintViolation<TaskCreateRequest>> violations =
                validator.validate(new TaskCreateRequest("   ", null, null));

        assertThat(fields(violations)).contains("title");
        assertThat(messages(violations)).anySatisfy(m -> assertThat(m).contains("title is required"));
    }

    @Test
    void tooShortTitle_isRejected() {
        Set<ConstraintViolation<TaskCreateRequest>> violations =
                validator.validate(new TaskCreateRequest("ab", null, null));

        assertThat(fields(violations)).contains("title");
    }

    @Test
    void tooLongDescription_isRejected() {
        String tooLong = "x".repeat(501);

        Set<ConstraintViolation<TaskCreateRequest>> violations =
                validator.validate(new TaskCreateRequest("Valid title", tooLong, null));

        assertThat(fields(violations)).contains("description");
    }

    @Test
    void statusUpdate_withoutStatus_isRejected() {
        Set<ConstraintViolation<TaskStatusUpdateRequest>> violations =
                validator.validate(new TaskStatusUpdateRequest(null));

        assertThat(fields(violations)).contains("status");
    }

    private static Set<String> fields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static Set<String> messages(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }
}
