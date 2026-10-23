package com.discoverdevops.library;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class BookService {

    private final Map<Long, Book> books = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public BookService() {
        add("The Phoenix Project", "Gene Kim");
        add("The DevOps Handbook", "Gene Kim");
        add("Site Reliability Engineering", "Betsy Beyer");
    }

    public List<Book> findAll() {
        return books.values().stream()
                .sorted(Comparator.comparingLong(Book::id))
                .toList();
    }

    public Optional<Book> findById(long id) {
        return Optional.ofNullable(books.get(id));
    }

    public Book add(String title, String author) {
        long id = sequence.incrementAndGet();
        Book book = new Book(id, title, author, true);
        books.put(id, book);
        return book;
    }

    public synchronized Book borrow(long id) {
        Book current = books.get(id);
        if (current == null) {
            throw new NoSuchElementException("Book " + id + " not found");
        }
        if (!current.available()) {
            throw new IllegalStateException("Book " + id + " is already borrowed");
        }
        Book updated = current.withAvailable(false);
        books.put(id, updated);
        return updated;
    }
}
