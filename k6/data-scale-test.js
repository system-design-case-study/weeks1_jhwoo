import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const queryLatency = new Trend('query_latency', true);

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DATA_SCALE = __ENV.DATA_SCALE || '10K';

const GANGNAM = { lat: 37.4979, lng: 127.0276 };
const RADII = [0.5, 1.0, 2.0, 5.0, 20.0];

export const options = {
  scenarios: {
    constant_load: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
  thresholds: {
    'query_latency': ['p(95)<1000'],
  },
  tags: {
    data_scale: DATA_SCALE,
  },
};

export default function () {
  for (const radius of RADII) {
    const lat = GANGNAM.lat + (Math.random() - 0.5) * 0.05;
    const lng = GANGNAM.lng + (Math.random() - 0.5) * 0.05;

    const url = `${BASE_URL}/api/search?latitude=${lat}&longitude=${lng}&radius=${radius}`;
    const res = http.get(url, { tags: { radius: `${radius}km` } });

    queryLatency.add(res.timings.duration);

    check(res, {
      [`${radius}km 반경 검색 성공`]: (r) => r.status === 200,
    });

    sleep(0.2);
  }
}
