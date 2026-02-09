package com.proximity.adapter.out.persistence.write.entity;

import com.proximity.domain.BusinessHours;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalTime;

@Entity
@Table(name = "business_hours")
public class BusinessHoursJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private BusinessJpaEntity business;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed;

    protected BusinessHoursJpaEntity() {
    }

    public BusinessHours toDomain() {
        Long businessId = (business != null) ? business.getId() : null;
        BusinessHours hours = new BusinessHours(businessId, dayOfWeek, openTime, closeTime, closed);
        hours.setId(id);
        return hours;
    }

    public static BusinessHoursJpaEntity fromDomain(BusinessHours hours) {
        BusinessHoursJpaEntity entity = new BusinessHoursJpaEntity();
        entity.id = hours.getId();
        entity.dayOfWeek = (short) hours.getDayOfWeek();
        entity.openTime = hours.getOpenTime();
        entity.closeTime = hours.getCloseTime();
        entity.closed = hours.isClosed();
        return entity;
    }

    public void setBusiness(BusinessJpaEntity business) { this.business = business; }

    public Long getId() { return id; }
    public BusinessJpaEntity getBusiness() { return business; }
    public short getDayOfWeek() { return dayOfWeek; }
    public LocalTime getOpenTime() { return openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public boolean isClosed() { return closed; }
}
