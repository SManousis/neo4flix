import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '15s',
  thresholds: {
    checks: ['rate==1.0'],
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const responses = [
    http.get(`${baseUrl}/api/v1/movies?page=0&size=1`),
    http.get(`${baseUrl}/api/v1/genres`),
  ];

  responses.forEach((response) => {
    check(response, {
      'catalog response is 2xx, 3xx, or 429': (r) =>
        (r.status >= 200 && r.status < 400) || r.status === 429,
    });
  });
  sleep(1);
}
