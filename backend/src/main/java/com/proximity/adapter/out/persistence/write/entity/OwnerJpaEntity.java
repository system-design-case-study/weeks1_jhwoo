package com.proximity.adapter.out.persistence.write.entity;

import com.proximity.domain.Owner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "owners")
public class OwnerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OwnerJpaEntity() {
    }

    public Owner toDomain() {
        Owner owner = new Owner(email, passwordHash, name);
        owner.setId(id);
        owner.setCreatedAt(createdAt);
        owner.setUpdatedAt(updatedAt);
        return owner;
    }

    public static OwnerJpaEntity fromDomain(Owner owner) {
        OwnerJpaEntity entity = new OwnerJpaEntity();
        entity.id = owner.getId();
        entity.email = owner.getEmail();
        entity.passwordHash = owner.getPasswordHash();
        entity.name = owner.getName();
        entity.createdAt = owner.getCreatedAt();
        entity.updatedAt = owner.getUpdatedAt();
        return entity;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
