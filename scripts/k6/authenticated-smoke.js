import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const password = __ENV.K6_PASSWORD;
const rateLimited = new Counter('rate_limited_responses');

if (!password) {
  throw new Error('Set K6_PASSWORD to a disposable test password; no password is embedded in this profile.');
}

// 429 is a deliberate, observable rate-limit outcome rather than a server error.
http.setResponseCallback(http.expectedStatuses(200, 201, 202, 204, 429));

export const options = {
  vus: Number(__ENV.VUS || 1),
  duration: __ENV.DURATION || '15s',
  thresholds: {
    checks: ['rate==1.0'],
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
  },
};

function accepted(response) {
  return (response.status >= 200 && response.status < 300) || response.status === 429;
}

export function setup() {
  const email = `k6-${Date.now()}-${Math.random().toString(36).slice(2)}@example.test`;
  const register = http.post(`${baseUrl}/api/v1/auth/register`, JSON.stringify({
    email,
    displayName: 'k6 Disposable User',
    password,
  }), { headers: { 'Content-Type': 'application/json' } });
  if (register.status !== 201) {
    throw new Error(`Disposable registration failed with HTTP ${register.status}`);
  }

  const loginRequest = () => http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  const login = loginRequest();
  if (login.status !== 200) {
    // setup failures do not invoke teardown; make one bounded cleanup attempt.
    const cleanupLogin = loginRequest();
    if (cleanupLogin.status === 200) {
      const cleanupToken = cleanupLogin.json('accessToken');
      http.del(`${baseUrl}/api/v1/users/me`, JSON.stringify({ password, code: null }), {
        headers: { Authorization: `Bearer ${cleanupToken}`, 'Content-Type': 'application/json' },
      });
    }
    throw new Error(`Disposable login failed with HTTP ${login.status}`);
  }
  return { email, token: login.json('accessToken') };
}

export default function (user) {
  const params = { headers: { Authorization: `Bearer ${user.token}` } };
  const responses = [
    http.get(`${baseUrl}/api/v1/recommendations/me?page=0&size=1`, params),
    http.get(`${baseUrl}/api/v1/ratings/me?page=0&size=1`, params),
    http.get(`${baseUrl}/api/v1/users/me/watchlist?page=0&size=1`, params),
  ];

  responses.forEach((response) => {
    if (response.status === 429) rateLimited.add(1);
    check(response, {
    'authenticated response is 2xx or 429': accepted,
    });
  });
  sleep(1);
}

export function teardown(user) {
  http.del(`${baseUrl}/api/v1/users/me`, JSON.stringify({ password, code: null }), {
    headers: {
      Authorization: `Bearer ${user.token}`,
      'Content-Type': 'application/json',
    },
  });
}
