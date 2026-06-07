// Spike Test — 3000명 / 급증 패턴

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    thresholds: {
        http_req_duration: ['p(99)<10000'],
    },
    scenarios: {
        spike_unread: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 3000 },
                { duration: '1m',  target: 3000 },
                { duration: '10s', target: 0    },
            ],
            gracefulRampDown: '10s',
            exec: 'unreadCount',
        },
        spike_list: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 3000 },
                { duration: '1m',  target: 3000 },
                { duration: '10s', target: 0    },
            ],
            gracefulRampDown: '10s',
            exec: 'notificationList',
            startTime: '2m',
        },
    },
};

const TOKENS = [
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhdXRob3JAa29yZWEuYWMua3IiLCJ0eXBlIjoiQUNDRVNTIiwiaWF0IjoxNzgwNzYwNDY0LCJleHAiOjE3ODA3NjIyNjR9.0n4DNXrOMCDTBJNi2eXiMVAFyNGX0Zg5Tyj2n9uOa4noDyxtoI-HfGxl_4V-xX8o',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhcHBsaWNhbnRAa29yZWEuYWMua3IiLCJ0eXBlIjoiQUNDRVNTIiwiaWF0IjoxNzgwNzYwNDY0LCJleHAiOjE3ODA3NjIyNjR9.6Gba2tqMkyNbERXIbDxqCs8XHcEad4-orUZvYwdHFmuUwyEZd2gJPw6MZ7PdMr_2',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJkYWxzdW5fcmluQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NjA0NjQsImV4cCI6MTc4MDc2MjI2NH0.pP853b2XnnzU9KAhiFQWTd1F0PNqzw_u_yZYFBv6ZJr6iOXYJipfcH4bUc7WMV6p',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hdXRob3JAbmF2ZXIuY29tIiwidHlwZSI6IkFDQ0VTUyIsImlhdCI6MTc4MDc2MDQ2NCwiZXhwIjoxNzgwNzYyMjY0fQ.zJQrInb9Ws2wg25dxAHpQFV1iX5Qe_lD-ig4TJOP640VAOE46NyMtGpDllwXdi5r',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQxQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NjA0NjQsImV4cCI6MTc4MDc2MjI2NH0.IM6tJKg604i2PDCg1Iam7CmYXJUkAcafR89tUEJjOlMt87qZmvynN6GXr3B7ditr',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQyQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NjA0NjQsImV4cCI6MTc4MDc2MjI2NH0.Z6Jl53aDj82am_RphOzEnB3YYoA2l2QarVnvvj1uWF0Y0FiiZMjjX_1NrKyDiUGb',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQzQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NjA0NjQsImV4cCI6MTc4MDc2MjI2NH0.etOIG1ZbxAwbDLyBYWzVC6yhxHSYEfDFSc8x8xyyMsy37sBJh2iwNZo3zu0gb9fh',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0MUBrb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NjA0NjQsImV4cCI6MTc4MDc2MjI2NH0.hTnOdnE-3nok0xbjbyQxKKuECc9krYFFntBiPS9kXnb2meHNj0o0PJMdN0fLEq0t',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0MkBrb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NjA0NjQsImV4cCI6MTc4MDc2MjI2NH0.sCpOzbl0X5Qn-AzW59q8ZsaNFX2lS_48Qzwf0DH7nTTU6-jPYyYeNmap_eCuPMcr',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0M0Brb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NjA0NjUsImV4cCI6MTc4MDc2MjI2NX0.Rl8RQzv_qfj4hYnuEmHdWMEGN4oig6BZc7f3eoGMSBdZZV9ECfiH-cZLzH5xW2os',
];

function getHeaders() {
    const token = TOKENS[Math.floor(Math.random() * TOKENS.length)];
    return {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
    };
}

export function unreadCount() {
    const res = http.get(
        'http://localhost:8080/api/v1/notifications/unread-count',
        { headers: getHeaders(), tags: { api: 'unread_count' } }
    );
    check(res, { 'unread-count status 200': (r) => r.status === 200 });
    sleep(1);
}

export function notificationList() {
    const res = http.get(
        'http://localhost:8080/api/v1/notifications?size=20',
        { headers: getHeaders(), tags: { api: 'notification_list' } }
    );
    check(res, { 'notification-list status 200': (r) => r.status === 200 });
    sleep(1);
}
