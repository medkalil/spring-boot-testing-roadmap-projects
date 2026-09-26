package com.example.todo;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryTodoRepository implements TodoRepository {

    private final Map<Long, Todo> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Todo save(Todo todo) {
        if (todo.getId() == null) {
            todo.setId(idGenerator.getAndIncrement());
        }
        store.put(todo.getId(), todo);
        return todo;
    }

    @Override
    public Optional<Todo> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Todo> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }
}