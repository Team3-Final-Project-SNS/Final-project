package com.example.team3final.domain.ai.matching.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 매칭 AI의 음식/메뉴 조건 검증에 쓰는 공통 키워드 유틸입니다.
 *
 * V3 추천 흐름에서는 pgvector가 자연어 조건과 가까운 게시글 후보를 먼저 찾고,
 * LLM이 그 후보 중 추천할 게시글 ID를 고릅니다. 다만 "치킨 먹고 싶어"처럼
 * 특정 메뉴가 명확한 요청에서도 벡터 유사도나 LLM 판단 때문에 메뉴 근거가 없는
 * 게시글이 섞일 수 있습니다.
 *
 * 이 유틸은 그런 경우를 막기 위해 아래 두 경로에서 같은 기준으로 사용됩니다.
 * 1. Tool 후보 복구: pgvector 후보가 비었거나 부족할 때, 명확한 메뉴 요청이면
 *    MySQL 후보 중 장소명/한마디에 메뉴 단서가 있는 글을 다시 찾습니다.
 * 2. LLM 추천 후검증: LLM이 반환한 recommendedPostIds가 실제 메뉴 조건의 근거를
 *    갖고 있는지 확인하고, 근거가 없는 후보는 카드 응답에서 제외합니다.
 *
 * 즉, 이 클래스는 추천 품질을 높이기 위한 간단한 규칙 기반 안전장치입니다.
 * 메뉴 판별을 LLM에게만 맡기지 않고 서버에서도 최소한의 근거를 확인합니다.
 */
public final class AiMatchingMenuEvidence {

    // 게시글 장소명(placeName)이나 한마디(content)에 직접 등장하면
    // "해당 메뉴와 관련 있는 게시글"이라고 인정할 대표 키워드입니다.
    //
    // 예:
    // - 사용자 요청: "치킨 먹고 싶어"
    // - 게시글 한마디: "후문 치킨집에서 같이 먹어요"
    // - "치킨"이 포함되므로 메뉴 조건 근거가 있는 후보로 인정합니다.
    //
    // 너무 넓은 단어를 넣으면 무관한 글까지 메뉴 후보로 살아남을 수 있으므로,
    // 실제 초기 데이터와 서비스에서 자주 쓰는 음식 단어 위주로 관리합니다.
    private static final List<String> MENU_KEYWORDS = List.of(
            "치킨",
            "닭",
            "국밥",
            "파스타",
            "떡볶이",
            "김밥",
            "샌드위치",
            "짜장",
            "짬뽕",
            "탕수육",
            "마라탕",
            "돈까스",
            "돈가스",
            "분식",
            "튀김",
            "카페"
    );

    // "중국 음식", "중국집", "중식 메뉴"처럼 범주형 요청이 들어왔을 때
    // 실제 게시글 단서와 비교하기 위해 확장하는 대표 메뉴 키워드입니다.
    //
    // 사용자가 "중국 음식"이라고 말했는데 게시글에는 "짜장면"만 적혀 있을 수 있습니다.
    // 이때 범주 단어만 비교하면 후보를 놓치므로, 대표 메뉴 단어까지 확장합니다.
    private static final List<String> CHINESE_MENU_KEYWORDS = List.of(
            "중국",
            "중식",
            "짜장",
            "짬뽕",
            "탕수육",
            "마라탕"
    );

    // 사용자가 중국 음식 범주를 의도했다고 볼 수 있는 입력 표현입니다.
    //
    // 주의:
    // "중식"은 한국어에서 점심 시간대를 뜻할 수도 있고, 중국 음식을 뜻할 수도 있습니다.
    // 그래서 대화 라우터/프롬프트 단계에서는 단독 "중식"을 보고 바로 메뉴로 단정하지 않습니다.
    // 이 목록은 이미 메뉴 검증 단계로 들어온 텍스트에서 중국 음식 범주를 대표 메뉴로
    // 확장하기 위한 용도로만 사용합니다.
    private static final List<String> CHINESE_MENU_INTENTS = List.of(
            "중국음식",
            "중국요리",
            "중국집",
            "중식메뉴",
            "중식"
    );

    // 특정 메뉴명이 없어도 "메뉴 추천", "음식 먹고 싶어", "요리 추천"처럼
    // 음식/메뉴 탐색 의도가 있다고 볼 수 있는 일반 표현입니다.
    //
    // 이 단어들은 "메뉴 의도 여부" 판단에는 사용하지만,
    // 실제 게시글 근거 토큰으로는 약하기 때문에 extractTokens()의 대표 메뉴 토큰과 구분합니다.
    private static final List<String> MENU_INTENT_KEYWORDS = List.of(
            "음식",
            "메뉴",
            "요리",
            "먹고싶"
    );

    private AiMatchingMenuEvidence() {
    }

    /**
     * 사용자 조건이 음식/메뉴 탐색 요청인지 판단합니다.
     *
     * 사용처:
     * - AiMatchingTool에서 pgvector 검색 결과가 비었을 때,
     *   "이 요청이 메뉴 조건인가?"를 판단해 MySQL 기반 후보 복구를 시도할지 결정합니다.
     *
     * true가 되는 예:
     * - "치킨 먹고 싶어"
     * - "중국 음식 추천해줘"
     * - "메뉴는 아무거나 괜찮아"
     *
     * false가 되는 예:
     * - "저녁에 먹을 사람"
     * - "책임비 낮은 순"
     * - "조용한 사람"
     */
    public static boolean hasMenuIntent(String text) {
        String normalized = normalize(text);
        return containsAny(normalized, MENU_INTENT_KEYWORDS)
                || containsAny(normalized, MENU_KEYWORDS)
                || containsAny(normalized, CHINESE_MENU_INTENTS);
    }

    /**
     * 사용자 조건이나 게시글 본문에서 메뉴 검증에 사용할 키워드만 추출합니다.
     *
     * 사용처:
     * - 사용자 요청에서 메뉴 근거 토큰을 뽑아 LLM 추천 결과를 후검증합니다.
     * - 게시글 텍스트에서 직접 등장한 메뉴 단서를 뽑아 후보 복구/비교에 사용합니다.
     *
     * 예:
     * - "치킨 먹고 싶어" -> ["치킨"]
     * - "중국 음식 먹고 싶어" -> ["중국", "중식", "짜장", "짬뽕", "탕수육", "마라탕"]
     * - "정문에서 조용하게 먹고 싶어" -> []
     *
     * 반환 타입을 List로 둔 이유:
     * - 프롬프트 답변과 추천 이유에서 "치킨, 짜장"처럼 순서가 있는 문장으로 보여줄 수 있습니다.
     * - 내부에서는 LinkedHashSet으로 중복을 제거하되 선언 순서를 유지합니다.
     */
    public static List<String> extractTokens(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        addContainedTokens(tokens, normalized, MENU_KEYWORDS);

        if (containsAny(normalized, CHINESE_MENU_INTENTS)) {
            tokens.addAll(CHINESE_MENU_KEYWORDS);
        }

        return List.copyOf(tokens);
    }

    /**
     * 후보 게시글 텍스트가 사용자 메뉴 조건의 실제 근거를 포함하는지 확인합니다.
     *
     * 사용처:
     * - LLM이 추천한 후보가 사용자의 음식 조건과 실제로 연결되는지 서버에서 한 번 더 확인합니다.
     *
     * 예:
     * - 사용자 요청 토큰: ["치킨"]
     * - 후보 텍스트: "후문 치킨집에서 만나요"
     * - 결과: true
     *
     * - 사용자 요청 토큰: ["치킨"]
     * - 후보 텍스트: "중앙도서관 앞에서 만나요"
     * - 결과: false
     *
     * false인 후보는 "치킨 조건에 맞는 추천"으로 카드에 내려가지 않게 됩니다.
     */
    public static boolean hasEvidence(String text, List<String> evidenceTokens) {
        if (text == null || evidenceTokens == null || evidenceTokens.isEmpty()) {
            return false;
        }

        String normalized = normalize(text);
        return evidenceTokens.stream()
                .map(AiMatchingMenuEvidence::normalize)
                .filter(token -> token.length() >= 2)
                .anyMatch(normalized::contains);
    }

    // 후보 키워드 중 normalized 텍스트에 포함된 단어만 tokens에 추가합니다.
    //
    // Set 구현체로 LinkedHashSet을 쓰는 이유:
    // - 중복 키워드는 제거합니다.
    // - MENU_KEYWORDS에 선언한 순서는 유지합니다.
    // - 결과 순서가 매번 같아야 로그, 테스트, 추천 이유 문장이 안정적으로 유지됩니다.
    private static void addContainedTokens(Set<String> tokens, String normalized, List<String> candidates) {
        for (String candidate : candidates) {
            if (normalized.contains(candidate)) {
                tokens.add(candidate);
            }
        }
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    // 메뉴 조건 비교용 간단 정규화입니다.
    //
    // 처리 내용:
    // - null은 빈 문자열로 변환합니다.
    // - 모든 공백을 제거합니다.
    // - 대소문자를 소문자로 통일합니다.
    //
    // 이유:
    // - "중국 음식"과 "중국음식"을 같은 표현으로 비교하기 위해서입니다.
    // - "Chicken"처럼 영문이 섞인 경우도 최소한 대소문자 차이로 실패하지 않게 합니다.
    //
    // 형태소 분석까지 하지 않는 이유:
    // - 이 유틸은 LLM/pgvector를 대체하는 검색 엔진이 아니라,
    //   명확한 메뉴 단서만 확인하는 가벼운 후검증 장치이기 때문입니다.
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text.replace(" ", "").toLowerCase();
    }
}
