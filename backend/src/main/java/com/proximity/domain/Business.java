package com.proximity.domain;

import java.time.OffsetDateTime;
import java.util.List;

public class Business {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    private Long id;
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private String phone;
    private String category;
    private Long ownerId;
    private List<BusinessHours> businessHours;
    private List<BusinessPhoto> photos;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected Business() {
    }

    public Business(String name, String address, double latitude, double longitude,
                    String phone, String category, Long ownerId) {
        validateCoordinates(latitude, longitude);
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.category = category;
        this.ownerId = ownerId;
    }

    public void updateLocation(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean isOwnedBy(Long ownerId) {
        return this.ownerId != null && this.ownerId.equals(ownerId);
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new IllegalArgumentException(
                    "위도는 " + MIN_LATITUDE + "에서 " + MAX_LATITUDE + " 사이의 값이어야 합니다");
        }
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new IllegalArgumentException(
                    "경도는 " + MIN_LONGITUDE + "에서 " + MAX_LONGITUDE + " 사이의 값이어야 합니다");
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getOwnerId() { return ownerId; }
    public List<BusinessHours> getBusinessHours() { return businessHours; }
    public void setBusinessHours(List<BusinessHours> businessHours) { this.businessHours = businessHours; }
    public List<BusinessPhoto> getPhotos() { return photos; }
    public void setPhotos(List<BusinessPhoto> photos) { this.photos = photos; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
