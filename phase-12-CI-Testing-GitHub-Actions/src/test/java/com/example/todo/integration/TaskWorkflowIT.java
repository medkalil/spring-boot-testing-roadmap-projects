package com.example.todo.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.todo.dto.TaskCreateRequest;
import com.example.todo.dto.TaskResponse;
import com.example.todo.entity.TaskStatus;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack integration test (Failsafe): the entire application - controller,
 * service, JPA, Flyway - running against a REAL PostgreSQL container started
 * by Testcontainers, exercised over real HTTP.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskWorkflowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void createTransitionEditAndDelete_overHttp() {
        // 1. Flyway seed is visible -> migrations ran in the full context.
        ResponseEntity<TaskResponse[]> list = rest.getForEntity("/api/tasks", TaskResponse[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Arrays.stream(list.getBody()).map(TaskResponse::title))
                .contains("Sample task (seeded)");

        // 2. Create -> 201, Location header, defaults to TODO.
        ResponseEntity<TaskResponse> created = rest.postForEntity(
                "/api/tasks",
                new TaskCreateRequest("Ship the CI pipeline", "docs and workflow", null),
                TaskResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getFirst("Location")).contains("/api/tasks/");
        assertThat(created.getBody().status()).isEqualTo(TaskStatus.TODO);
        Long id = created.getBody().id();

        // 3. Forward transitions -> 200.
        assertThat(changeStatus(id, "IN_PROGRESS").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changeStatus(id, "DONE").getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4. Business rules over HTTP: DONE is terminal -> 409.
        ResponseEntity<String> edit = rest.exchange(
                "/api/tasks/{id}", HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "Renamed task")), String.class, id);
        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changeStatus(id, "TODO").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 5. Unknown id -> 404.
        ResponseEntity<String> missing =
                rest.exchange("/api/tasks/999999", HttpMethod.GET, null, String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 6. Validation -> 400 (title shorter than 3 characters).
        ResponseEntity<String> invalid = rest.postForEntity(
                "/api/tasks", new TaskCreateRequest("x", null, null), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 7. Delete -> 204, afterwards -> 404.
        ResponseEntity<String> deleted =
                rest.exchange("/api/tasks/{id}", HttpMethod.DELETE, null, String.class, id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> gone =
                rest.exchange("/api/tasks/{id}", HttpMethod.GET, null, String.class, id);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> changeStatus(Long id, String status) {
        return rest.exchange("/api/tasks/{id}/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", status)), String.class, id);
    }
}
