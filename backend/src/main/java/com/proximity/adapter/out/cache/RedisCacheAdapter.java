package com.proximity.adapter.out.cache;

import com.proximity.application.dto.BusinessDetailResponse;
import com.proximity.application.dto.SearchResponse;
import com.proximity.application.port.out.CachePort;
import com.proximity.application.service.GridCacheKeyGenerator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisCacheAdapter implements CachePort {

    private static final Duration SEARCH_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration BUSINESS_CACHE_TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<SearchResponse> getSearchCache(String cacheKey) {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        if (value instanceof SearchResponse response) {
            return Optional.of(response);
        }
        return Optional.empty();
    }

    @Override
    public void putSearchCache(String cacheKey, SearchResponse response) {
        redisTemplate.opsForValue().set(cacheKey, response, SEARCH_CACHE_TTL);
    }

    @Override
    public Optional<BusinessDetailResponse> getBusinessCache(Long id) {
        String key = GridCacheKeyGenerator.businessKey(id);
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof BusinessDetailResponse response) {
            return Optional.of(response);
        }
        return Optional.empty();
    }

    @Override
    public void putBusinessCache(Long id, BusinessDetailResponse response) {
        String key = GridCacheKeyGenerator.businessKey(id);
        redisTemplate.opsForValue().set(key, response, BUSINESS_CACHE_TTL);
    }

    @Override
    public void invalidateSearchCache(double lat, double lng) {
        List<String> patterns = GridCacheKeyGenerator.adjacentSearchKeyPatterns(lat, lng);
        for (String pattern : patterns) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

    @Override
    public void invalidateBusinessCache(Long id) {
        String key = GridCacheKeyGenerator.businessKey(id);
        redisTemplate.delete(key);
    }
}
