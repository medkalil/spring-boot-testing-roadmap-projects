package com.example.todo.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.todo.dto.TaskCreateRequest;
import com.example.todo.dto.TaskResponse;
import com.example.todo.dto.TaskStatusUpdateRequest;
import com.example.todo.entity.TaskStatus;
import com.example.todo.exception.ConflictException;
import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice test (Surefire): HTTP contract of the controller with a mocked
 * service - validation, status codes and error mapping, no database.
 */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    @Test
    void create_validRequest_returns201WithLocationHeader() throws Exception {
        when(taskService.create(any(TaskCreateRequest.class))).thenAnswer(invocation -> {
            TaskCreateRequest request = invocation.getArgument(0);
            return new TaskResponse(7L, request.title().trim(), request.description(),
                    TaskStatus.TODO, Instant.now());
        });

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskCreateRequest("Buy milk", "2%", null))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/tasks/7")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void create_blankTitle_returns400WithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("title")));

        verifyNoInteractions(taskService);
    }

    @Test
    void create_malformedStatusValue_returns400() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Valid title\",\"status\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("malformed request body")));
    }

    @Test
    void list_invalidStatusFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("status")));
    }

    @Test
    void list_returnsTasks() throws Exception {
        when(taskService.list(null))
                .thenReturn(List.of(new TaskResponse(1L, "Existing", null, TaskStatus.TODO,
                        Instant.now())));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Existing"));
    }

    @Test
    void get_unknownId_returns404WithMessage() throws Exception {
        when(taskService.get(99L)).thenThrow(new ResourceNotFoundException("task 99 was not found"));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("task 99 was not found"));
    }

    @Test
    void changeStatus_conflict_returns409WithMessage() throws Exception {
        when(taskService.changeStatus(eq(1L), any()))
                .thenThrow(new ConflictException("invalid status transition DONE -> TODO"));

        mockMvc.perform(patch("/api/tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TODO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("invalid status transition DONE -> TODO"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/3"))
                .andExpect(status().isNoContent());
    }
}
