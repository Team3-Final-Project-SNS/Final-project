// src/types/kakao.maps.d.ts

declare global {
    interface Window {
        kakao: typeof kakao;
    }

    namespace kakao.maps {
        // ── 지도 ──────────────────────────────────────────
        class Map {
            constructor(container: HTMLElement, options: MapOptions);
            setCenter(latlng: LatLng): void;
            setLevel(level: number): void;
            getLevel(): number;
        }

        interface MapOptions {
            center: LatLng;
            level: number;
        }

        // ── 좌표 ──────────────────────────────────────────
        class LatLng {
            constructor(lat: number, lng: number);
            getLat(): number;
            getLng(): number;
        }

        // ── 기본 마커 ─────────────────────────────────────
        class Marker {
            constructor(options: MarkerOptions);
            setMap(map: Map | null): void;
            setPosition(latlng: LatLng): void;
        }

        interface MarkerOptions {
            map?: Map;
            position: LatLng;
        }

        // ── 커스텀 오버레이 ───────────────────────────────
        class CustomOverlay {
            constructor(options: CustomOverlayOptions);
            setMap(map: Map | null): void;
            setPosition(latlng: LatLng): void;
        }

        interface CustomOverlayOptions {
            map?: Map;
            position: LatLng;
            content: string | HTMLElement;
            yAnchor?: number;
            xAnchor?: number;
        }

        // ── 원 ────────────────────────────────────────────
        class Circle {
            constructor(options: CircleOptions);
            setMap(map: Map | null): void;
        }

        interface CircleOptions {
            map?: Map;
            center: LatLng;
            radius: number;
            strokeWeight?: number;
            strokeColor?: string;
            strokeOpacity?: number;
            strokeStyle?: string;
            fillColor?: string;
            fillOpacity?: number;
        }

        // ── geometry 라이브러리 ───────────────────────────
        namespace geometry {
            namespace Sphere {
                function computeDistanceBetween(latlng1: LatLng, latlng2: LatLng): number;
            }
        }

        // ── 장소 검색 서비스 ← declare global 안으로 이동 ─
        namespace services {
            class Places {
                keywordSearch(
                    keyword: string,
                    callback: (result: any[], status: string) => void
                ): void;
            }

            enum Status {
                OK = 'OK',
                ZERO_RESULTS = 'ZERO_RESULTS',
                ERROR = 'ERROR',
            }
        }

        // ── SDK 초기화 콜백 ───────────────────────────────
        function load(callback: () => void): void;
    }
}

export {};