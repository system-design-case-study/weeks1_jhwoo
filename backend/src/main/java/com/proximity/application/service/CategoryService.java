package com.proximity.application.service;

import com.proximity.application.port.in.CategoryUseCase;
import com.proximity.application.port.out.CategoryPort;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CategoryService implements CategoryUseCase {

    private static final String CACHE_KEY = "categories";
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final CategoryPort categoryPort;
    private final RedisTemplate<String, Object> redisTemplate;

    public CategoryService(CategoryPort categoryPort,
                           RedisTemplate<String, Object> redisTemplate) {
        this.categoryPort = categoryPort;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getCategories() {
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached instanceof List<?> list) {
            return (List<String>) list;
        }
        List<String> categories = categoryPort.findDistinctCategories();
        redisTemplate.opsForValue().set(CACHE_KEY, categories, CACHE_TTL);
        return categories;
    }

    @Scheduled(fixedRate = 12, timeUnit = TimeUnit.HOURS)
    public void refreshCategories() {
        List<String> categories = categoryPort.findDistinctCategories();
        redisTemplate.opsForValue().set(CACHE_KEY, categories, CACHE_TTL);
    }

    @PostConstruct
    void initCategories() {
        refreshCategories();
    }
}
