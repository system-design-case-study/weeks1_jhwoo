-- 한국 좌표 범위 내 무작위 사업장 데이터 생성
-- 실행: psql -U proximity -d proximity -f generate-seed-data.sql
-- 또는 환경변수 SEED_COUNT로 생성 수량 조절

-- 기본 Owner 생성 (시드 데이터용)
INSERT INTO owners (email, password_hash, name, created_at, updated_at)
VALUES ('seed@proximity.dev', '$2a$10$dummyhashforseeddata000000000000000000000000000', '시드 사업주', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

-- 카테고리 목록
CREATE TEMPORARY TABLE IF NOT EXISTS seed_categories (category TEXT);
TRUNCATE seed_categories;
INSERT INTO seed_categories VALUES ('카페'), ('식당'), ('편의점'), ('약국'), ('병원'), ('미용실'), ('세탁소'), ('문구점'), ('서점'), ('꽃집');

-- 한국 주요 도시 좌표 범위
-- 서울: 37.413~37.715, 126.734~127.183
-- 부산: 35.053~35.238, 128.852~129.219
-- 대전: 36.282~36.430, 127.299~127.480
-- 대구: 35.797~35.922, 128.480~128.696

-- 시드 데이터 생성 함수
CREATE OR REPLACE FUNCTION generate_proximity_seed(total_count INT) RETURNS VOID AS $$
DECLARE
    owner_id BIGINT;
    categories TEXT[];
    city_weights DOUBLE PRECISION[] := ARRAY[0.50, 0.20, 0.15, 0.15];
    city_lat_min DOUBLE PRECISION[] := ARRAY[37.413, 35.053, 36.282, 35.797];
    city_lat_max DOUBLE PRECISION[] := ARRAY[37.715, 35.238, 36.430, 35.922];
    city_lng_min DOUBLE PRECISION[] := ARRAY[126.734, 128.852, 127.299, 128.480];
    city_lng_max DOUBLE PRECISION[] := ARRAY[127.183, 129.219, 127.480, 128.696];
    gangnam_ratio DOUBLE PRECISION := 0.1;
    gangnam_lat_center DOUBLE PRECISION := 37.4979;
    gangnam_lng_center DOUBLE PRECISION := 127.0276;
    i INT;
    city_idx INT;
    rand_val DOUBLE PRECISION;
    lat DOUBLE PRECISION;
    lng DOUBLE PRECISION;
    cat TEXT;
BEGIN
    SELECT id INTO owner_id FROM owners WHERE email = 'seed@proximity.dev';
    categories := ARRAY['카페', '식당', '편의점', '약국', '병원', '미용실', '세탁소', '문구점', '서점', '꽃집'];

    FOR i IN 1..total_count LOOP
        rand_val := random();

        IF rand_val < gangnam_ratio THEN
            lat := gangnam_lat_center + (random() - 0.5) * 0.02;
            lng := gangnam_lng_center + (random() - 0.5) * 0.02;
        ELSIF rand_val < gangnam_ratio + city_weights[1] THEN
            city_idx := 1;
            lat := city_lat_min[city_idx] + random() * (city_lat_max[city_idx] - city_lat_min[city_idx]);
            lng := city_lng_min[city_idx] + random() * (city_lng_max[city_idx] - city_lng_min[city_idx]);
        ELSIF rand_val < gangnam_ratio + city_weights[1] + city_weights[2] THEN
            city_idx := 2;
            lat := city_lat_min[city_idx] + random() * (city_lat_max[city_idx] - city_lat_min[city_idx]);
            lng := city_lng_min[city_idx] + random() * (city_lng_max[city_idx] - city_lng_min[city_idx]);
        ELSIF rand_val < gangnam_ratio + city_weights[1] + city_weights[2] + city_weights[3] THEN
            city_idx := 3;
            lat := city_lat_min[city_idx] + random() * (city_lat_max[city_idx] - city_lat_min[city_idx]);
            lng := city_lng_min[city_idx] + random() * (city_lng_max[city_idx] - city_lng_min[city_idx]);
        ELSE
            city_idx := 4;
            lat := city_lat_min[city_idx] + random() * (city_lat_max[city_idx] - city_lat_min[city_idx]);
            lng := city_lng_min[city_idx] + random() * (city_lng_max[city_idx] - city_lng_min[city_idx]);
        END IF;

        cat := categories[1 + (floor(random() * array_length(categories, 1)))::INT];

        INSERT INTO businesses (name, address, latitude, longitude, phone, category, owner_id, location, created_at, updated_at)
        VALUES (
            cat || ' ' || i,
            '시드 주소 ' || i,
            lat,
            lng,
            '010-' || lpad((random() * 10000)::INT::TEXT, 4, '0') || '-' || lpad((random() * 10000)::INT::TEXT, 4, '0'),
            cat,
            owner_id,
            ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography,
            NOW(),
            NOW()
        );

        IF i % 10000 = 0 THEN
            RAISE NOTICE '% rows 삽입 완료', i;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- 사용법:
-- 10K 데이터: SELECT generate_proximity_seed(10000);
-- 100K 데이터: SELECT generate_proximity_seed(100000);
-- 500K 데이터: SELECT generate_proximity_seed(500000);
-- 1M 데이터: SELECT generate_proximity_seed(1000000);

-- 기본 실행 (10K)
SELECT generate_proximity_seed(10000);

-- 정리
DROP FUNCTION IF EXISTS generate_proximity_seed(INT);
DROP TABLE IF EXISTS seed_categories;
