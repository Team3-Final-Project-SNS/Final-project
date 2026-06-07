// Smoke Test — 10명 / 2분
// 목적: 스크립트 정상 동작 및 API 기본 응답 확인

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    thresholds: {
        // P99 3초 이하 기준 (Smoke는 여유있게)
        http_req_duration: ['p(99)<3000'],
    },
    scenarios: {
        smoke_unread: {
            executor: 'constant-vus',
            vus: 10,
            duration: '1m',
            exec: 'unreadCount',
        },
        smoke_list: {
            executor: 'constant-vus',
            vus: 10,
            duration: '1m',
            exec: 'notificationList',
            startTime: '1m10s',
        },
    },
};

const TOKENS = [
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhdXRob3JAa29yZWEuYWMua3IiLCJ0eXBlIjoiQUNDRVNTIiwiaWF0IjoxNzgwNzU3NDc1LCJleHAiOjE3ODA3NTkyNzV9.P9-IWeFGDuM1soZsWB6yj12PxJpFjGgpGbnhZKpRKYOegATRjpiSc6QtC4e79SkO',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhcHBsaWNhbnRAa29yZWEuYWMua3IiLCJ0eXBlIjoiQUNDRVNTIiwiaWF0IjoxNzgwNzU3NDc1LCJleHAiOjE3ODA3NTkyNzV9.Dg4XWGjBiPPaNX2HxdZWkgN6Kir2qrk_xYdJBkefcngy_IpVTRyCxJoQWG0b9AFC',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJkYWxzdW5fcmluQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NTc0NzUsImV4cCI6MTc4MDc1OTI3NX0.B2duEjOdJl_7jb9Bb5r3NbLhujE6gZrZnY08fDQJ_EIESzbgHaEd55c8vuc_4nSC',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hdXRob3JAbmF2ZXIuY29tIiwidHlwZSI6IkFDQ0VTUyIsImlhdCI6MTc4MDc1NzQ3NiwiZXhwIjoxNzgwNzU5Mjc2fQ.xNeBbt6VKGzqXeAppRN-vrrpd45FWCJwXaIpKxt20JWmind_4muqsDOj-1Xuz9CG',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQxQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NTc0NzYsImV4cCI6MTc4MDc1OTI3Nn0.kLjfa2U8jsf2HTVazNdZJpSzKswajVc5u2Iqka1bDcio06wlk6PULoXqrSE2uSCd',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQyQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NTc0NzYsImV4cCI6MTc4MDc1OTI3Nn0.C0n4-7vYYHU9oR8mmcV2500oKtgsk_9Qcy7eHmX3x438XsGhncoK8ZMw5j3Gri9R',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQzQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NTc0NzYsImV4cCI6MTc4MDc1OTI3Nn0.c8SRY-ByewWj7IkKX7M9cbx72HnOOUPZFwqTHatMh8oxCXkUXrZnDwscwF21Rgca',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0MUBrb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NTc0NzYsImV4cCI6MTc4MDc1OTI3Nn0.5ReJDl86D2-cEbPNbVUU1ymOl0e1BjqGOC9TAxmBqJ0PteESuzor-4S0x9KA-tQ5',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0MkBrb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NTc0NzYsImV4cCI6MTc4MDc1OTI3Nn0.sM0zyhPLHV17nhDIvnBMJ68HRZ-J9WebIyexADgGEVzMiow2tPfV6aQPV4zxOI4v',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0M0Brb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA3NTc0NzYsImV4cCI6MTc4MDc1OTI3Nn0.-pHxpCwMoa5rX2Gx8Y2pAPA4jY5OBKdA9RHclPOod4cPYvKRxQLPrJ-73keptE9C',
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