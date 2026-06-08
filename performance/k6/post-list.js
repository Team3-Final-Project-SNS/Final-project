import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;
const PROFILE = __ENV.PROFILE || 'smoke';

const successRate = new Rate('post_list_success_rate');
const postListRequests = new Counter('post_list_requests');

const profiles = {
  smoke: {
    scenarios: {
      smoke: {
        executor: 'constant-vus',
        vus: 1,
        duration: '1m',
      },
    },
  },
  load: {
    scenarios: {
      load: {
        executor: 'ramping-vus',
        stages: [
          { duration: '1m', target: 10 },
          { duration: '3m', target: 10 },
          { duration: '1m', target: 0 },
        ],
      },
    },
  },
  stress: {
    scenarios: {
      stress: {
        executor: 'ramping-vus',
        stages: [
          { duration: '1m', target: 10 },
          { duration: '2m', target: 30 },
          { duration: '2m', target: 50 },
          { duration: '1m', target: 0 },
        ],
      },
    },
  },
};

if (!profiles[PROFILE]) {
  throw new Error(`Unknown PROFILE: ${PROFILE}. Use smoke, load, or stress.`);
}

export const options = {
  ...profiles[PROFILE],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    post_list_success_rate: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  if (!ACCESS_TOKEN) {
    throw new Error('ACCESS_TOKEN is required. Run with -e ACCESS_TOKEN=...');
  }

  const page = pickPage();
  const url = `${BASE_URL}/api/v1/posts?status=OPEN&page=${page}&size=20`;

  const response = http.get(url, {
    headers: {
      Authorization: `Bearer ${ACCESS_TOKEN}`,
      Accept: 'application/json',
    },
    tags: {
      api: 'post-list',
      status_filter: 'OPEN',
    },
  });

  postListRequests.add(1);

  const ok = check(response, {
    'status is 200': (res) => res.status === 200,
    'response is successful': (res) => {
      try {
        return res.json('success') === true;
      } catch (error) {
        return false;
      }
    },
  });

  successRate.add(ok);
  sleep(1);
}

function pickPage() {
  const random = Math.random();
  if (random < 0.7) {
    return 0;
  }
  if (random < 0.9) {
    return 1;
  }
  return Math.floor(Math.random() * 5);
}
