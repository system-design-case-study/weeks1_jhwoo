package com.proximity.adapter.out.persistence.write.entity;

import com.proximity.domain.BusinessPhoto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "business_photos")
public class BusinessPhotoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private BusinessJpaEntity business;

    @Column(name = "photo_url", nullable = false, length = 1000)
    private String photoUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected BusinessPhotoJpaEntity() {
    }

    public BusinessPhoto toDomain() {
        Long businessId = (business != null) ? business.getId() : null;
        BusinessPhoto photo = new BusinessPhoto(businessId, photoUrl, displayOrder);
        photo.setId(id);
        photo.setCreatedAt(createdAt);
        return photo;
    }

    public static BusinessPhotoJpaEntity fromDomain(BusinessPhoto photo) {
        BusinessPhotoJpaEntity entity = new BusinessPhotoJpaEntity();
        entity.id = photo.getId();
        entity.photoUrl = photo.getPhotoUrl();
        entity.displayOrder = photo.getDisplayOrder();
        entity.createdAt = photo.getCreatedAt();
        return entity;
    }

    public void setBusiness(BusinessJpaEntity business) { this.business = business; }

    public Long getId() { return id; }
    public BusinessJpaEntity getBusiness() { return business; }
    public String getPhotoUrl() { return photoUrl; }
    public int getDisplayOrder() { return displayOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
