package com.example.todo;

import com.example.todo.exception.TodoNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    public Todo createTodo(Todo todo) {
        if (todo == null) {
            throw new IllegalArgumentException("Todo cannot be null");
        }
        if (todo.getTitle() == null || todo.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Todo title cannot be empty");
        }
        return todoRepository.save(todo);
    }

    public List<Todo> getAllTodos() {
        return todoRepository.findAll();
    }

    public Todo getTodoById(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException("Todo not found with id: " + id));
    }

    public Todo updateTodo(Long id, Todo updatedTodo) {
        if(updatedTodo == null){
            throw new IllegalArgumentException("Todo cannot be null");
        }
        if(updatedTodo.getTitle() == null || updatedTodo.getTitle().trim().isEmpty()){
            throw new IllegalArgumentException("Todo title cannot be empty");
        }
        
        Todo existingTodo = getTodoById(id);
        existingTodo.setTitle(updatedTodo.getTitle());
        existingTodo.setCompleted(updatedTodo.isCompleted());
        return todoRepository.save(existingTodo);
    }

    public void deleteTodo(Long id) {
        if (!todoRepository.existsById(id)) {
            throw new TodoNotFoundException("Todo not found with id: " + id);
        }
        todoRepository.deleteById(id);
    }
}