package com.example.team3final.domain.ai.matching.rag;


/**
 * 매칭 AI 검색/RAG 확장용 Retriever입니다.
 *
 * 현재 매칭 AI는 MySQL 기반 Tool 조회 결과를 후보로 사용합니다.
 * 이 클래스는 추후 PostgreSQL + pgvector 기반 RAG 검색을 도입할 때,
 * 사용자 자연어 조건과 의미적으로 유사한 모집글 postId를 검색하는 역할로 확장할 예정입니다.
 *
 * 예정 흐름:
 * 1. 모집글의 장소, 내용, 시간, 메뉴 키워드 등을 임베딩하여 PostgreSQL Vector DB에 저장
 * 2. 사용자의 자연어 요청을 임베딩으로 변환
 * 3. pgvector 유사도 검색으로 관련 postId 후보 조회
 * 4. 조회된 postId를 MySQL의 실제 Post 데이터와 다시 검증
 * 5. 같은 학교, 모집중 상태, 신청 가능 여부, 책임비 포인트 조건을 확인한 뒤 LLM에 전달
 *
 * RAG는 검색 품질을 높이기 위한 보조 검색 계층이며,
 * 최종 권한/상태/포인트 검증은 기존 MySQL 도메인 데이터를 기준으로 수행합니다.
 */
public class AiMatchingRetriever {
}
