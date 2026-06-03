package com.example.team3final.common.utils;

// GPS 거리 계산 공통 유틸 클래스
public class GpsUtils {

    private GpsUtils() {}

    // 지구 반지름
    private static final int EARTH_RADIUS_METERS = 6371000;

    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {

        // 위도/경도 차이를 라디안으로 변환 (삼각함수는 라디안 단위 사용)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        // Haversine 공식 적용
        // a: 두 점 사이 중심각의 절반에 대한 사인 제곱값
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        // c: 중심각 (라디안)
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // 실제 거리 = 지구 반지름 × 중심각
        return EARTH_RADIUS_METERS * c;
    }
}
