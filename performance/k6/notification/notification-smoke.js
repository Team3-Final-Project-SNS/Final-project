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
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhdXRob3JAa29yZWEuYWMua3IiLCJ0eXBlIjoiQUNDRVNTIiwiaWF0IjoxNzgwODU3MTgyLCJleHAiOjE3ODA4NTg5ODJ9.dwoqPetxJ5oY-YoQdOyVMP2D2mjYf7vaeYC8ncz1i1qD7bRK-M7zll_2wJiu06dH',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhcHBsaWNhbnRAa29yZWEuYWMua3IiLCJ0eXBlIjoiQUNDRVNTIiwiaWF0IjoxNzgwODU3MTgyLCJleHAiOjE3ODA4NTg5ODJ9.uEpq1Ne4mMsWgSvn_QkuqW_Y7nQtnam8lb8ZEzvc3d7X81Yao1NdTFczds5Pn1a9',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJkYWxzdW5fcmluQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA4NTcxODIsImV4cCI6MTc4MDg1ODk4Mn0.rdMRey_G1OX6_IyD74eGcMSVL_1zu08SWSuYw-vF4i4nJabhSVmmp0R1CW-fHekf',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hdXRob3JAbmF2ZXIuY29tIiwidHlwZSI6IkFDQ0VTUyIsImlhdCI6MTc4MDg1NzE4MiwiZXhwIjoxNzgwODU4OTgyfQ.gqBZ24tz5pmg8WcIyb8LuOfwEDfulyRf-r5XHYJduzur9PLTdl0O0rFDASMmy3dj',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQxQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA4NTcxODIsImV4cCI6MTc4MDg1ODk4Mn0.32aS1K4HDFB2t5GEsoqc-muPalOrBeLH518wyqPBE2vG2T0t4K5czmjfoKLK0NxV',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQyQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA4NTcxODIsImV4cCI6MTc4MDg1ODk4Mn0.fEpn-eIywc8IPJrSfRmNnDf1_UoskrNyQkmLb2AC5bJ0BiJuumqkzCJLB1PSEA1e',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJuYXZlci1hcHBsaWNhbnQzQG5hdmVyLmNvbSIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA4NTcxODIsImV4cCI6MTc4MDg1ODk4Mn0.RHLEeKS8TrfjeYFatTsmFOGfNi0_HChDbZrpDYVRmz3iqCmd2e5LoMDhqrELPfpC',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0MUBrb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA4NTcxODIsImV4cCI6MTc4MDg1ODk4Mn0.sl8oGxi53qAOr10c8d8tAj049D-HN1lSee6Vr2CExoGqI87Ru6REgqwy5x72OEYN',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0MkBrb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA4NTcxODMsImV4cCI6MTc4MDg1ODk4M30.Ua9mMF4QlYpOLRL4qm6V970niQ1sn7PebnKpraTo1yTziFb1H8w_mULdrDbaUcX7',
    'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ0ZXN0M0Brb3JlYS5hYy5rciIsInR5cGUiOiJBQ0NFU1MiLCJpYXQiOjE3ODA4NTcxODMsImV4cCI6MTc4MDg1ODk4M30.kEWC4n_tuLPHkHIXvT8A2Y8LFGDcaWvCpifPJgXc3XGqL2SY5blkGX9jn5HJ4ZKz',
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