package com.proximity.domain;

import java.time.OffsetDateTime;

public class Owner {

    private Long id;
    private String email;
    private String passwordHash;
    private String name;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected Owner() {
    }

    public Owner(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
