package com.example.book;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

@DisplayName("Book Service Tests")
@ExtendWith(MockitoExtension.class)
class BookServiceTest { 

    @Mock
    BookRepository bookRepository;

    @InjectMocks
    BookService bookService;

    private Book book;

    @BeforeEach
    void setUp() {
        book = new Book(1L, "Book 1", "Author me", 20.0);
    }

    @Nested
    @DisplayName("Creating a Book")
    class CreatingBookTests {

        @Test
        @DisplayName("Should return saved book")
        void testCreatingSuccessfully() {
            Book request = new Book(null, "Book 1", "Author me", 20.0);

            when(bookRepository.save(request)).thenReturn(book);

            Book result = bookService.createBook(request);

            assertEquals(book, result);
            verify(bookRepository).save(request);
        }

        @Test
        @DisplayName("Should Through when Book Null")
        void testThroughtsWhenBookNull() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> bookService.createBook(null)
            );
            verify(bookRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should Validate Book Title is Not Null")
        void testBookTitleNotNull() {
            Book request = new Book(1L, null, "author", 20);

            assertThrows(
                IllegalArgumentException.class,
                () -> bookService.createBook(request)
            );
            
            verify(bookRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create book and pass book to repo and test it with ArgumentCaptor")
        void createBook_passesBookToRepository() {
            // Arrange
            Book book = new Book();
            book.setTitle("Clean Code");
            book.setAuthor("Robert C. Martin");

            ArgumentCaptor<Book> bookCaptor =
                    ArgumentCaptor.forClass(Book.class);

            when(bookRepository.save(any(Book.class)))
                    .thenReturn(book);

            // Act
            bookService.createBook(book);

            // Assert
            verify(bookRepository).save(bookCaptor.capture());

            Book capturedBook = bookCaptor.getValue();

            assertEquals("Clean Code", capturedBook.getTitle());
            assertEquals("Robert C. Martin", capturedBook.getAuthor());
        }
    }

    @Nested
    @DisplayName("Getting Books")
     class GetBooksTests {

        @Test
        @DisplayName("Should return existing book by id")
        void testGetExistingBookById() {
            Long bookId = 1L;

            when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));

            Book result = bookService.getBookById(bookId);

            assertEquals(book, result);
            verify(bookRepository).findById(bookId);
        }

        @Test
        @DisplayName("Should through when book not foudn")
        void testthroughtWhenBookNotFound() {
            Long bookId = 1L;

            when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                () -> bookService.getBookById(bookId));
            assertEquals(
                "Book not found with id: " + bookId,
                exception.getMessage()
            );
        }

    }

    @Nested
    @DisplayName("Deleting Books")
    class DeletingTests {

        @Test
        @DisplayName("Should delete a book")
        void testDeleteBook() {
            Long bookId = 1L;

            when(bookRepository.existsById(bookId)).thenReturn(true);

            bookService.deleteBook(bookId);

            verify(bookRepository).existsById(bookId);
            verify(bookRepository).deleteById(bookId);
        }

        @Test
        @DisplayName("Should throught if book not found")
        void testThghouthIfBookNotFound() {
            Long bookId = 1L;

            when(bookRepository.existsById(bookId)).thenReturn(false);

            assertThrows(IllegalArgumentException.class, () -> bookService.deleteBook(bookId));

            verify(bookRepository).existsById(bookId);
            verify(bookRepository, never()).deleteById(bookId);
        }
    }
}