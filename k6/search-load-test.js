import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const searchLatency = new Trend('search_latency', true);
const detailLatency = new Trend('detail_latency', true);
const writeLatency = new Trend('write_latency', true);
const searchErrorRate = new Rate('search_errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const GANGNAM = { lat: 37.4979, lng: 127.0276 };
const ALLOWED_RADII = [0.5, 1.0, 2.0, 5.0, 20.0];

function randomLat() {
  return 33.0 + Math.random() * 5.0;
}

function randomLng() {
  return 124.0 + Math.random() * 8.0;
}

function randomRadius() {
  return ALLOWED_RADII[Math.floor(Math.random() * ALLOWED_RADII.length)];
}

export const options = {
  scenarios: {
    ramp_up: {
      executor: 'ramping-arrival-rate',
      startRate: 290,
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 500,
      stages: [
        { duration: '1m', target: 290 },
        { duration: '2m', target: 500 },
        { duration: '2m', target: 1000 },
        { duration: '1m', target: 290 },
      ],
    },
  },
  thresholds: {
    'search_latency': ['p(95)<1000', 'p(99)<2000'],
    'search_errors': ['rate<0.01'],
  },
};

export default function () {
  const scenario = Math.random();

  if (scenario < 0.80) {
    doSearch();
  } else if (scenario < 0.95) {
    doDetailView();
  } else {
    doWrite();
  }
}

function doSearch() {
  const isHotspot = Math.random() < 0.3;
  const lat = isHotspot ? GANGNAM.lat + (Math.random() - 0.5) * 0.01 : randomLat();
  const lng = isHotspot ? GANGNAM.lng + (Math.random() - 0.5) * 0.01 : randomLng();
  const radius = randomRadius();

  const url = `${BASE_URL}/api/search?latitude=${lat}&longitude=${lng}&radius=${radius}`;
  const res = http.get(url);

  searchLatency.add(res.timings.duration);
  check(res, {
    '검색 응답 200': (r) => r.status === 200,
    '검색 결과 포함': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.businesses !== undefined;
      } catch (e) {
        return false;
      }
    },
  }) || searchErrorRate.add(1);

  sleep(0.1);
}

function doWrite() {
  const lat = randomLat();
  const lng = randomLng();
  const payload = JSON.stringify({
    name: `부하테스트 업체 ${Date.now()}`,
    address: `테스트 주소 ${Math.floor(Math.random() * 10000)}`,
    latitude: lat,
    longitude: lng,
    phone: '010-0000-0000',
    category: '카페',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.AUTH_TOKEN || ''}`,
    },
  };

  const res = http.post(`${BASE_URL}/api/businesses`, payload, params);

  writeLatency.add(res.timings.duration);
  check(res, {
    '쓰기 응답 성공 또는 인증 필요': (r) => r.status === 201 || r.status === 401,
  });

  sleep(0.1);
}

function doDetailView() {
  const businessId = Math.floor(Math.random() * 1000) + 1;
  const url = `${BASE_URL}/api/businesses/${businessId}`;
  const res = http.get(url);

  detailLatency.add(res.timings.duration);
  check(res, {
    '상세 조회 응답 200 또는 404': (r) => r.status === 200 || r.status === 404,
  });

  sleep(0.1);
}
