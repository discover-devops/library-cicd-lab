package com.discoverdevops.library;

public record Book(long id, String title, String author, boolean available) {

    public Book withAvailable(boolean newAvailability) {
        return new Book(id, title, author, newAvailability);
    }
}
