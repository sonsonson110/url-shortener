package com.urlshortener.jpademo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Demo entity used only for JPA pitfall demonstrations (Phase 0).
 * Lives in test scope — not part of the production domain.
 */
@Entity
@Table(name = "demo_books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    /*
     * @ManyToOne is LAZY here too. When we load a Book, we don't
     * automatically load its Author. This is the safer default for
     * @ManyToOne relationships as well.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author author;

    // Required by JPA
    protected Book() {}

    public Book(String title) {
        this.title = title;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Author getAuthor() { return author; }

    // Package-private: only Author.addBook() should set this
    void setAuthor(Author author) {
        this.author = author;
    }
}
