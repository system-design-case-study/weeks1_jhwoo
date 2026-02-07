package com.proximity.adapter.out.persistence.write.entity;

import com.proximity.domain.Business;
import com.proximity.domain.BusinessHours;
import com.proximity.domain.BusinessPhoto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "businesses")
public class BusinessJpaEntity {

    private static final int SRID = 4326;
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, precision = 9, scale = 6)
    private double latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private double longitude;

    @Column(columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String category;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BusinessHoursJpaEntity> businessHours = new ArrayList<>();

    @OneToMany(mappedBy = "business", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BusinessPhotoJpaEntity> photos = new ArrayList<>();

    protected BusinessJpaEntity() {
    }

    public Business toDomain() {
        Business business = new Business(name, address, latitude, longitude, phone, category, ownerId);
        business.setId(id);
        business.setCreatedAt(createdAt);
        business.setUpdatedAt(updatedAt);

        if (businessHours != null) {
            List<BusinessHours> hours = businessHours.stream()
                    .map(BusinessHoursJpaEntity::toDomain)
                    .toList();
            business.setBusinessHours(hours);
        }

        if (photos != null) {
            List<BusinessPhoto> photoList = photos.stream()
                    .map(BusinessPhotoJpaEntity::toDomain)
                    .toList();
            business.setPhotos(photoList);
        }

        return business;
    }

    public static BusinessJpaEntity fromDomain(Business business) {
        BusinessJpaEntity entity = new BusinessJpaEntity();
        entity.id = business.getId();
        entity.ownerId = business.getOwnerId();
        entity.name = business.getName();
        entity.address = business.getAddress();
        entity.latitude = business.getLatitude();
        entity.longitude = business.getLongitude();
        entity.location = GEOMETRY_FACTORY.createPoint(
                new Coordinate(business.getLongitude(), business.getLatitude()));
        entity.phone = business.getPhone();
        entity.category = business.getCategory();
        entity.createdAt = business.getCreatedAt();
        entity.updatedAt = business.getUpdatedAt();

        if (business.getBusinessHours() != null) {
            for (BusinessHours hours : business.getBusinessHours()) {
                BusinessHoursJpaEntity hoursEntity = BusinessHoursJpaEntity.fromDomain(hours);
                hoursEntity.setBusiness(entity);
                entity.businessHours.add(hoursEntity);
            }
        }

        if (business.getPhotos() != null) {
            for (BusinessPhoto photo : business.getPhotos()) {
                BusinessPhotoJpaEntity photoEntity = BusinessPhotoJpaEntity.fromDomain(photo);
                photoEntity.setBusiness(entity);
                entity.photos.add(photoEntity);
            }
        }

        return entity;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Point getLocation() { return location; }
    public String getPhone() { return phone; }
    public String getCategory() { return category; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<BusinessHoursJpaEntity> getBusinessHours() { return businessHours; }
    public List<BusinessPhotoJpaEntity> getPhotos() { return photos; }
}
