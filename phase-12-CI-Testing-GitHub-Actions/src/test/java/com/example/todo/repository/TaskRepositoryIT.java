package com.example.todo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.todo.entity.Task;
import com.example.todo.entity.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository integration test (Failsafe): a REAL PostgreSQL container via
 * Testcontainers. No locally installed database is needed - here or in CI.
 * Flyway is explicitly imported so the schema comes from V1/V2, not Hibernate.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class TaskRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TaskRepository repository;

    @Test
    void flywayCreatedTheSchemaAndSeededData() {
        // Passing this proves V1 (table) + V2 (seed) actually ran on PostgreSQL.
        assertThat(repository.findByStatus(TaskStatus.TODO))
                .extracting(Task::getTitle)
                .contains("Sample task (seeded)");
    }

    @Test
    void findByStatusReturnsOnlyMatchingTasks() {
        Task task = new Task();
        task.setTitle("Repository IT task");
        task.setStatus(TaskStatus.IN_PROGRESS);
        repository.saveAndFlush(task);

        assertThat(repository.findByStatus(TaskStatus.IN_PROGRESS))
                .extracting(Task::getTitle)
                .contains("Repository IT task");
        assertThat(repository.findByStatus(TaskStatus.DONE)).isEmpty();
    }
}
