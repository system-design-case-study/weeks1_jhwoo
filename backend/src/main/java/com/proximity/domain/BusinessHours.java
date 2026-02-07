package com.proximity.domain;

import java.time.LocalTime;

public class BusinessHours {

    private static final int MIN_DAY_OF_WEEK = 0;
    private static final int MAX_DAY_OF_WEEK = 6;

    private Long id;
    private Long businessId;
    private int dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean closed;

    protected BusinessHours() {
    }

    public BusinessHours(Long businessId, int dayOfWeek, LocalTime openTime,
                         LocalTime closeTime, boolean closed) {
        validateDayOfWeek(dayOfWeek);
        this.businessId = businessId;
        this.dayOfWeek = dayOfWeek;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.closed = closed;
    }

    public boolean isOpen(LocalTime time) {
        if (closed) {
            return false;
        }
        if (openTime == null || closeTime == null) {
            return false;
        }
        return !time.isBefore(openTime) && !time.isAfter(closeTime);
    }

    private void validateDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < MIN_DAY_OF_WEEK || dayOfWeek > MAX_DAY_OF_WEEK) {
            throw new IllegalArgumentException(
                    "요일은 " + MIN_DAY_OF_WEEK + "(월)에서 " + MAX_DAY_OF_WEEK + "(일) 사이의 값이어야 합니다");
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBusinessId() { return businessId; }
    public void setBusinessId(Long businessId) { this.businessId = businessId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
}
