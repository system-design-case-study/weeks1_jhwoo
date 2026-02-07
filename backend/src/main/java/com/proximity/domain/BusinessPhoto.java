package com.proximity.domain;

import java.time.OffsetDateTime;

public class BusinessPhoto {

    private Long id;
    private Long businessId;
    private String photoUrl;
    private int displayOrder;
    private OffsetDateTime createdAt;

    protected BusinessPhoto() {
    }

    public BusinessPhoto(Long businessId, String photoUrl, int displayOrder) {
        this.businessId = businessId;
        this.photoUrl = photoUrl;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
