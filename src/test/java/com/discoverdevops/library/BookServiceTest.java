package com.discoverdevops.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class BookServiceTest {

    private final BookService service = new BookService();

    @Test
    void catalogStartsWithThreeBooksInIdOrder() {
        var books = service.findAll();

        assertEquals(3, books.size());
        assertEquals(1L, books.get(0).id());
        assertEquals(3L, books.get(2).id());
    }

    @Test
    void addAssignsNextIdAndMarksBookAvailable() {
        Book added = service.add("Accelerate", "Nicole Forsgren");

        assertEquals(4L, added.id());
        assertTrue(added.available());
        assertTrue(service.findById(4L).isPresent());
    }

    @Test
    void findByIdReturnsEmptyForUnknownBook() {
        assertTrue(service.findById(999L).isEmpty());
    }

    @Test
    void borrowMarksBookAsUnavailable() {
        Book borrowed = service.borrow(1L);

        assertFalse(borrowed.available());
        assertFalse(service.findById(1L).orElseThrow().available());
    }

    @Test
    void borrowingTheSameBookTwiceIsRejected() {
        service.borrow(2L);

        assertThrows(IllegalStateException.class, () -> service.borrow(2L));
    }

    @Test
    void borrowingAnUnknownBookIsRejected() {
        assertThrows(NoSuchElementException.class, () -> service.borrow(999L));
    }
}
