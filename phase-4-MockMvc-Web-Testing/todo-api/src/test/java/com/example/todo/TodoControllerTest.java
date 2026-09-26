package com.example.todo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.todo.exception.TodoNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// @WebMvcTest auto-registers the @RestControllerAdvice (GlobalExceptionHandler),
// so mocked service exceptions are handled and assertable:
//   TodoNotFoundException          -> 404
//   IllegalArgumentException      -> 400
//   HttpMessageNotReadableException -> 400
//   Exception                     -> 500
// so no setUp function needed.
@WebMvcTest (TodoController.class)
class TodoControllerTest {

    @Autowired 
    private MockMvc mockMvc;

    @MockBean 
    private TodoService todoService;

    private String asJsonString(Object obj) throws Exception {
        return new ObjectMapper().writeValueAsString(obj);
    }

    // POST
    // valid request → 201
    // invalid request → 400
    // service failure → appropriate error
    @Test
    void testCreateValidTodo() throws Exception {
        Todo request = new Todo(null, "Test Todo", false);
        Todo todo = new Todo(1L, "Test Todo", false);

        when(todoService.createTodo(request)).thenReturn(todo);

        mockMvc.perform(
            post("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(request))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Test Todo"))
        .andExpect(jsonPath("$.completed").value(false));
    }

@Test
    void testCreateInvalidTodo() throws Exception {
        when(todoService.createTodo(any())).thenThrow(new IllegalArgumentException("Todo cannot be null"));

        mockMvc.perform(
            post("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(new Todo()))
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Todo cannot be null"));
    }

    @Test
    void testCreateTodoTitleEmpty() throws Exception {
        Todo request = new Todo(1L, "", false);

        when(todoService.createTodo(request))
            .thenThrow(new IllegalArgumentException("Todo title cannot be empty"));

        mockMvc.perform(
            post("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(request))
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Todo title cannot be empty"));
    }

    // GET all
    // todos exist → 200
    // no todos → 200 + empty array
    @Test
    void testGetAllTodos() throws Exception {
        Todo todo1 = new Todo(1L, "Test Todo 1", false);
        Todo todo2 = new Todo(2L, "Test Todo 2", true);

        when(todoService.getAllTodos()).thenReturn(List.of(todo1, todo2));

        mockMvc.perform(
            get("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size()").value(2));
    }

    @Test
    void testGetAllTodosEmpty() throws Exception {
        when(todoService.getAllTodos()).thenReturn(List.of());

        mockMvc.perform(
            get("/api/todos")
            .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size()").value(0));
    }

    // GET one
    // exists → 200
    // doesn't exist → 404
    @Test
    void testGetTodoByIdExists() throws Exception {
        Todo todo = new Todo(1L, "Test Todo", false);

        when(todoService.getTodoById(1L)).thenReturn(todo);

        mockMvc.perform(
            get("/api/todos/{id}", 1L)
            .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1L))
        .andExpect(jsonPath("$.title").value("Test Todo"))
        .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void testGetTodoByIdNotFound() throws Exception {
        when(todoService.getTodoById(99L)).thenThrow(new TodoNotFoundException("Todo not found with id: " + 99L));

        mockMvc.perform(
            get("/api/todos/{id}", 99L)
            .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Todo not found with id: 99"));
    }

    // PUT
    // valid ID → 200
    // unknown ID → 404
    // invalid request → 400
    @Test
    void testUpdateTodoValid() throws Exception {
        Todo updatedTodo = new Todo(1L, "Updated Todo", true);

        when(todoService.updateTodo(1L, updatedTodo)).thenReturn(updatedTodo);

        mockMvc.perform(
            put("/api/todos/{id}", 1L)
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(updatedTodo))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated Todo"))
        .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void testUpdateEmptyTitleRequest() throws Exception {
        Todo request = new Todo(1L, null, false);
        when(todoService.updateTodo(1L, request))
            .thenThrow(new IllegalArgumentException("Todo title cannot be empty"));

        mockMvc.perform(
            put("/api/todos/{id}", 1L)
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(request))
        )
        .andExpect(status().isBadRequest());
    }


    // DELETE
    // exists → 204
    // unknown ID → 404
    @Test
    void testDeleteTodo() throws Exception {
        Long id = 1L;

        doNothing().when(todoService).deleteTodo(id);

        mockMvc.perform(
            delete("/api/todos/{id}", id)
        )
        .andExpect(status().isNoContent());
    }
    
    @Test
    void testDeleteTodoNotFound() throws Exception {
        Long id = 1L;

        doThrow(new TodoNotFoundException("Todo not found with id: " + id))
            .when(todoService)
            .deleteTodo(id);

        mockMvc.perform(
            delete("/api/todos/{id}", id)
        )
        .andExpect(status().is(404));
    }

}