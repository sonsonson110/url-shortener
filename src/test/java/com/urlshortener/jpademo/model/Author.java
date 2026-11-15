package com.urlshortener.jpademo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo entity used only for JPA pitfall demonstrations (Phase 0).
 * Lives in test scope — not part of the production domain.
 *
 * <p>The @OneToMany relationship to {@link Book} is LAZY by default,
 * which is what enables the N+1 problem to occur.
 */
@Entity
@Table(name = "demo_authors")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /*
     * FetchType.LAZY (the default) means: "Don't load books when you load
     * an Author. Wait until someone actually asks for them."
     *
     * This is the correct default — but it becomes an N+1 problem when you
     * access books inside a loop over many authors.
     */
    @OneToMany(
            mappedBy = "author",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Book> books = new ArrayList<>();

    // Required by JPA
    protected Author() {}

    public Author(String name) {
        this.name = name;
    }

    /**
     * Always use this helper to add a book.
     * Bidirectional relationships require both sides to be kept in sync:
     * the books list on the Author AND the author field on the Book.
     * Forgetting one side is a classic JPA bug.
     */
    public void addBook(Book book) {
        books.add(book);
        book.setAuthor(this);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<Book> getBooks() { return books; }
}
