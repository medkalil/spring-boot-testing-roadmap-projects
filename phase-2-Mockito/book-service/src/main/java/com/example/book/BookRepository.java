package com.example.book;

import java.util.List;
import java.util.Optional;

// here we don't even need the impl of the repo cz will be mocking it in the test
public interface BookRepository {
    Book save(Book book);
    Optional<Book> findById(Long id);
    List<Book> findAll();
    void deleteById(Long id);
    boolean existsById(Long id);
}
