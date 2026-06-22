# 최형민 트러블슈팅

한끼팟의 AI 추천, RAG 검색, pgvector 저장, 모니터링 및 알림 운영 과정에서 발생한 문제와 해결 과정을 정리한 문서입니다.

<a id="ai-프로젝트-구조-설계-및-매칭-ai-추천-정확도-개선"></a>

<details>
<summary><strong>AI 프로젝트 구조 설계 및 매칭 AI 추천 정확도 개선</strong></summary>

## AI 프로젝트 구조 설계 및 매칭 AI 추천 정확도 개선

### 1-1. AI 프로젝트 구조 설계 및 매칭 AI 추천 정확도 개선

### 1. 트러블슈팅 개요

| 구분 | 내용 |
| --- | --- |
| 트러블슈팅 주제 | AI 프로젝트 구조 설계 및 매칭 AI 추천 방식 개선 |
| 발생 위치 | AI 도메인 구조 설계, 매칭 AI 게시글 추천 로직 |
| 주요 문제 | AI 기능별 구조 분리가 어렵고, MySQL posts 테이블만으로는 자연어 기반 게시글 추천 정확도가 낮았음 |
| 초기 방식 | MySQL posts 테이블 기반 게시글 조회 및 Tool Calling 추천 |
| 개선 방식 | PostgreSQL pgvector 기반 의미 검색 도입 후 MySQL posts 테이블 재검증 |
| 개선 결과 | 자연어 조건 기반 추천 정확도 향상, 실제 서비스 규칙과 추천 결과의 일치성 강화 |

---

### 2. 문제 상황

AI 기능을 구현하면서 가장 먼저 어려웠던 부분은 AI 프로젝트 구조를 어떻게 잡을지 결정하는 것이었다.

기존 프로젝트는 게시글, 유저, 매칭, 포인트, 신고와 같은 일반적인 도메인 중심으로 구성되어 있었다. 따라서 대부분의 기능은 Controller, Service, Repository, Entity 계층으로 비교적 명확하게 나눌 수 있었다.

하지만 AI 기능은 일반 CRUD 기능과 다르게 단순히 데이터를 조회하고 저장하는 것만으로 끝나지 않았다. 프롬프트 관리, 대화 메모리 저장, Tool Calling, RAG 문서 검색, AI 호출 메트릭 저장, fallback 처리, SSE 스트리밍 응답 등 여러 요소가 함께 필요했다.

또한 AI 기능은 하나만 존재하는 것이 아니라 다음과 같이 여러 기능으로 확장되었다.

| AI 기능 | 주요 역할 | 사용하는 데이터 |
| --- | --- | --- |
| 매칭 AI | 사용자에게 식사팟 게시글 추천 | 게시글, 유저, 매칭, 포인트 |
| 고객센터 AI | 서비스 이용 문의 답변 | 정책 문서, 사용자 상태 |
| 관리자 전용 AI | 신고, 이의제기, 운영 판단 보조 | 신고, 게시글, 유저, 결제, 운영 정책 |

이처럼 AI 기능마다 목적과 사용하는 데이터가 달랐기 때문에, 기존 도메인 안에 AI 코드를 흩어놓으면 유지보수가 어려워질 수 있다고 판단했다.

예를 들어 매칭 AI 코드를 `domain/match`에 넣고, 고객센터 AI 코드를 `domain/support`에 넣고, 관리자 AI 코드를 `domain/report`에 넣으면 처음에는 단순해 보일 수 있다. 하지만 프롬프트 관리, AI 호출 메트릭, RAG 검색, 대화 메모리 같은 공통 기능이 여러 도메인에 중복될 가능성이 있었다.

따라서 AI 기능은 별도의 `domain/ai` 패키지로 분리하고, 그 안에서 공통 기능과 개별 기능을 나누는 구조가 필요했다.

---

### 3. AI 프로젝트 구조 설계

AI 도메인은 공통 기능과 기능별 AI를 분리하는 방식으로 구성했다.

```
src/main/java/com/example/team3final/domain/ai
├── common
│   ├── entity
│   │   └── AiCallMetric.java
│   ├── enums
│   │   ├── AiFeature.java
│   │   ├── AiPromptType.java
│   │   ├── AiCallStatus.java
│   │   └── AiErrorType.java
│   ├── repository
│   │   └── AiCallMetricRepository.java
│   └── service
│       ├── AiCallMetricService.java
│       └── AiCallMetricServiceImpl.java
│
├── prompt
│   ├── entity
│   │   └── AiPromptTemplate.java
│   ├── repository
│   │   └── AiPromptTemplateRepository.java
│   └── service
│       └── AiPromptFileService.java
│
├── rag
│   ├── config
│   │   └── AiRagVectorStoreConfig.java
│   ├── entity
│   │   └── AiRagDocumentIndex.java
│   ├── repository
│   │   └── AiRagDocumentIndexRepository.java
│   └── service
│       ├── AiRagEtlService.java
│       ├── AiRagRetrieverService.java
│       └── AiRagRetrieverServiceImpl.java
│
├── matching
│   ├── controller
│   │   └── AiMatchingController.java
│   ├── service
│   │   ├── AiMatchingService.java
│   │   └── AiMatchingServiceImpl.java
│   ├── tool
│   │   ├── AiMatchingTool.java
│   │   └── AiMatchingSessionTool.java
│   ├── repository
│   │   ├── AiMatchingChatMemoryRepository.java
│   │   ├── AiMatchingChatMessageRepository.java
│   │   └── PostVectorRepository.java
│   ├── entity
│   │   ├── AiMatchingChatMemory.java
│   │   └── AiMatchingChatMessage.java
│   └── rag
│       └── AiMatchingRetriever.java
│
├── support
│   ├── controller
│   │   └── AiSupportController.java
│   ├── service
│   │   └── AiSupportServiceImpl.java
│   ├── tool
│   │   └── AiSupportTool.java
│   └── entity
│       └── AiSupportChatMemory.java
│
└── report
    ├── controller
    │   └── AiReportController.java
    ├── service
    │   └── AiReportServiceImpl.java
    ├── tool
    │   └── AiReportTool.java
    └── entity
        └── AiReportChatMemory.java
```

현재 문서에서도 AI 기능은 매칭 AI, 고객센터 AI, 관리자 전용 AI로 분리되어 있으며, 공통 기반으로 `AiProperties`, `AiPromptTemplate`, `AiPromptFileService`, `AiCallMetric`, `AiRagRetrieverService`, `PostVectorRepository` 등을 사용하도록 정리되어 있다.

---

### 4. AI 구조 분리 기준

| 패키지 | 역할 | 분리 이유 |
| --- | --- | --- |
| `common` | AI 호출 메트릭, 공통 enum, 공통 상태 관리 | 모든 AI 기능에서 공통으로 사용하는 운영 데이터 관리 |
| `prompt` | 프롬프트 템플릿 메타데이터 및 파일 로딩 | 프롬프트를 코드와 분리하고 버전 관리하기 위함 |
| `rag` | RAG 문서 색인 및 검색 | 고객센터, 관리자 AI 등 정책 문서 기반 답변에 활용 |
| `matching` | 매칭 AI 전용 추천 기능 | 게시글, 유저, 매칭, 포인트 검증이 필요하므로 별도 분리 |
| `support` | 고객센터 AI 기능 | 서비스 정책과 사용자 상태 기반 답변을 담당 |
| `report` | 관리자 전용 AI 기능 | 신고, 이의제기, 운영 판단 보조 기능을 담당 |

이렇게 구조를 나누면서 AI 기능별 책임이 명확해졌다.

공통 기능은 재사용할 수 있고, 매칭 AI, 고객센터 AI, 관리자 AI는 각자의 목적에 맞게 독립적으로 확장할 수 있게 되었다.

---

### 5. 초기 매칭 AI 구현 방식

AI 프로젝트 구조를 정리한 뒤, 매칭 AI는 처음에 MySQL의 posts 테이블만 사용하여 게시글 추천을 구현했다.

초기 매칭 AI의 목표는 사용자가 자연어로 원하는 식사 조건을 입력하면, AI가 실제 모집글 중에서 적절한 게시글을 추천하는 것이었다.

예를 들어 사용자가 다음과 같이 입력한다고 가정했다.

```
오늘 저녁에 조용하게 밥 먹을 사람 추천해줘
```

초기 처리 흐름은 다음과 같았다.

```
사용자 자연어 입력
→ Rewrite Query Transformer로 검색 조건 보완
→ LLM이 AiMatchingTool 호출
→ MySQL posts 테이블 조회
→ 같은 학교 게시글 필터링
→ 모집 중 상태 확인
→ 만남 시간 확인
→ 책임비와 인원 조건 확인
→ 본인 게시글 제외
→ 이미 신청한 게시글 제외
→ 포인트 충족 여부 확인
→ 추천 후보 반환
```

이 방식은 구현이 비교적 단순했다. 이미 서비스에서 사용 중인 MySQL posts 테이블이 있었고, 게시글의 작성자, 만남 시간, 장소, 한마디, 책임비, 모집 인원 같은 정보도 모두 MySQL에 저장되어 있었기 때문이다.

또한 실제 서비스 규칙을 검증하기에는 MySQL 조회 방식이 적합했다. 게시글이 OPEN 상태인지, 로그인 사용자의 학교와 같은 학교 게시글인지, 만남 시간이 지나지 않았는지, 사용자가 이미 신청한 게시글인지, 사용자의 포인트가 책임비보다 충분한지 등은 MySQL의 실제 데이터를 기준으로 판단해야 했기 때문이다.

---

### 6. MySQL 기반 추천 방식의 한계

하지만 MySQL posts 테이블만 사용한 추천 방식은 자연어 기반 추천 정확도에서 한계가 있었다.

| 문제 | 설명 |
| --- | --- |
| 자연어 의미 검색 한계 | “조용하게”, “가볍게”, “든든하게” 같은 표현을 단순 조건 조회로 처리하기 어려움 |
| 키워드 불일치 문제 | 게시글에 “말수가 적어도 괜찮아요”라고 적혀 있으면 “조용한 분위기” 요청과 연결하기 어려움 |
| 메뉴 조건 처리 한계 | “튀김” 요청을 “치킨”, “돈까스”, “분식”과 연결하기 어려움 |
| 추천 이유 설득력 부족 | 후보와 사용자 조건의 연결성이 약하면 추천 이유도 자연스럽지 않음 |
| 확장성 부족 | 게시글 수와 표현이 늘어날수록 단순 조회 방식만으로 품질 개선이 어려움 |

예를 들어 사용자가 “조용하게 밥 먹을 사람”을 요청했을 때, 게시글에 “말수가 적어도 괜찮아요”라고 적혀 있다면 사람은 두 표현이 의미적으로 가깝다고 판단할 수 있다. 하지만 단순 MySQL 조회에서는 “조용”이라는 키워드가 직접 포함되어 있지 않으면 좋은 후보를 놓칠 수 있었다.

또한 사용자가 “튀김 먹고 싶어”라고 입력했을 때, 게시글에는 “치킨”, “돈까스”, “분식”처럼 구체적인 메뉴명만 들어 있을 수 있다. 이 경우 사람이 보기에는 튀김류 음식과 관련이 있지만, 단순 문자열 검색만으로는 이러한 범주형 의미를 처리하기 어려웠다.

결과적으로 MySQL posts 테이블만으로는 사용자의 자연어 요청과 게시글 내용을 의미적으로 연결하는 데 한계가 있었다.

---

### 7. 개선 방향

문제를 해결하기 위해 단순 MySQL 조회 방식에서 PostgreSQL pgvector 기반 벡터 검색 방식으로 변경했다.

개선 방향은 다음과 같이 정리할 수 있다.

| 구분 | 기존 방식 | 개선 방식 |
| --- | --- | --- |
| 후보 검색 | MySQL posts 테이블 조건 조회 | PostgreSQL pgvector 기반 의미 검색 |
| 검색 기준 | 키워드, 상태, 시간, 학교 조건 | 사용자 자연어와 게시글 embedding 유사도 |
| 검증 방식 | MySQL 조회 결과를 그대로 검증 | pgvector 후보를 MySQL에서 재검증 |
| 장점 | 구현이 단순하고 실제 DB 조회가 쉬움 | 자연어 조건과 게시글 의미를 더 잘 연결 |
| 한계 보완 | 의미 기반 검색 부족 | MySQL 재검증으로 서비스 규칙 유지 |

개선 후에는 pgvector와 MySQL의 역할을 분리했다.

| 구성 요소 | 역할 |
| --- | --- |
| PostgreSQL pgvector | 사용자의 자연어 조건과 의미적으로 가까운 게시글 후보 검색 |
| MySQL posts 테이블 | 실제 게시글 상태, 학교, 시간, 책임비, 인원, 신청 가능 여부 재검증 |
| LLM | 사용자 조건 해석 및 추천 이유 생성 |
| Tool Calling | 내부 DB 조회 및 서비스 규칙 검증 |
| 프론트엔드 | AI 답변과 추천 게시글 카드 분리 출력 |

---

### 8. 개선 후 매칭 AI 처리 흐름

PostgreSQL pgvector 도입 후 매칭 AI의 처리 흐름은 다음과 같이 변경되었다.

```
사용자 자연어 입력
→ Rewrite Query Transformer로 검색 조건 보완
→ 사용자 요청을 검색에 적합한 문장으로 재작성
→ pgvector 기반 게시글 의미 검색
→ 사용자 요청과 의미적으로 가까운 postId 후보 추출
→ MySQL posts 테이블에서 실제 게시글 재조회
→ 같은 학교 여부 검증
→ 모집 상태 OPEN 여부 검증
→ 만남 시간이 현재 이후인지 검증
→ 책임비, 모집 인원, 현재 신청 인원 검증
→ 본인 게시글 제외
→ 이미 신청한 게시글 제외
→ 사용자 포인트가 책임비보다 충분한지 검증
→ 최종 추천 후보 반환
→ LLM이 추천 이유를 자연어로 생성
→ 프론트엔드에서 추천 답변과 게시글 카드를 분리해 출력
```

이 구조의 핵심은 pgvector와 MySQL의 역할을 분리한 것이다.

pgvector는 자연어 의미 검색을 담당한다. 사용자가 “조용하게 먹고 싶어”라고 입력하면 단순히 “조용”이라는 단어가 들어간 게시글만 찾는 것이 아니라, “말수가 적어도 괜찮아요”, “차분하게 식사할 분”, “스터디 끝나고 조용히 식사하실 분”처럼 의미적으로 가까운 게시글을 후보로 찾을 수 있다.

반면 MySQL은 실제 서비스 규칙 검증을 담당한다. pgvector에서 의미적으로 가까운 후보가 나왔더라도, 해당 게시글이 이미 마감되었거나, 사용자의 학교와 다르거나, 만남 시간이 지났거나, 사용자가 이미 신청한 글이라면 추천하면 안 된다. 그래서 최종 추천 전에는 반드시 MySQL posts 테이블과 관련 테이블을 기준으로 다시 검증하도록 했다.

---

### 9. 개선 결과

| 개선 항목 | 개선 내용 |
| --- | --- |
| 자연어 조건 반영 | 사용자가 정확한 키워드를 입력하지 않아도 의미적으로 가까운 게시글 검색 가능 |
| 메뉴 조건 처리 | 치킨, 돈까스, 분식 등 메뉴 표현과 사용자 요청의 의미 연결성 향상 |
| 분위기 조건 처리 | 조용함, 가벼움, 빠른 식사 등 분위기 기반 추천 가능 |
| 추천 후보 탐색 | 단순 키워드 검색보다 더 넓은 후보 탐색 가능 |
| 서비스 규칙 검증 | MySQL 재검증을 통해 실제 신청 가능한 게시글만 추천 |
| 프론트엔드 안정성 | 추천 답변과 게시글 카드 데이터를 분리해 UI 안정성 향상 |

PostgreSQL pgvector를 도입한 뒤에는 단순 키워드 검색보다 사용자의 자연어 조건에 더 가까운 게시글을 찾을 수 있게 되었다.

또한 pgvector 검색 결과를 그대로 추천하지 않고 MySQL posts 테이블 기준으로 다시 검증했기 때문에, 실제로 신청할 수 없는 게시글이 추천되는 문제를 줄일 수 있었다.

결과적으로 매칭 AI는 사용자의 자연어 조건을 의미적으로 해석하고, 실제 서비스 규칙에 맞는 게시글만 추천하는 구조로 개선되었다.

---

### 10. 최종 정리

이번 트러블슈팅을 통해 AI 기능은 단순히 LLM API를 연결하는 것만으로는 충분하지 않다는 것을 알게 되었다.

AI 기능을 실제 서비스에 적용하려면 프로젝트 구조, 프롬프트 관리, 대화 메모리, Tool Calling, RAG 검색, 벡터 검색, 메트릭 저장, fallback 처리까지 함께 고려해야 했다.

처음에는 AI 프로젝트 구조를 잡는 것부터 어려웠다. AI 기능이 매칭, 고객센터, 관리자 전용 기능으로 나뉘면서 공통 기능과 개별 기능을 분리해야 했고, 프롬프트, RAG, Tool Calling, 대화 메모리, 메트릭 저장 구조를 함께 고려해야 했다.

또한 매칭 AI는 처음에 MySQL posts 테이블만 사용해 게시글 추천을 구현했지만, 자연어 조건 기반 추천 정확도에 한계가 있었다. 이를 해결하기 위해 PostgreSQL pgvector 기반 벡터 검색을 도입했고, pgvector로 후보를 찾은 뒤 MySQL에서 다시 검증하는 구조로 개선했다.

결과적으로 AI 프로젝트 구조는 `domain/ai` 하위에 공통 기능과 개별 AI 기능을 분리하는 방식으로 정리했고, 매칭 AI는 MySQL 기반 단순 조회 구조에서 PostgreSQL pgvector 기반 의미 검색과 MySQL 재검증을 함께 사용하는 구조로 개선할 수 있었다.

---

</details>

---

<a id="ai-매칭-rag-threshold-설정값-불일치로-인한-추천-품질-저하"></a>

<details>
<summary><strong>AI 매칭 RAG Threshold 설정값 불일치로 인한 추천 품질 저하</strong></summary>

## AI 매칭 RAG Threshold 설정값 불일치로 인한 추천 품질 저하

## 문제 상황

매칭 AI 기능을 테스트하는 과정에서 사용자의 채팅 내용과 관련성이 낮은 게시물이 추천되는 문제가 발생했다.

매칭 AI는 사용자가 챗봇에 입력한 문장을 분석해, 관련 있는 밥약 게시물을 추천하는 기능이다. 사용자가 특정 메뉴, 분위기, 시간대, 식사 목적 등을 입력하면 AI가 이를 바탕으로 게시물 후보를 찾고, 그중 가장 적절한 게시물을 추천하는 구조로 설계했다.

예를 들어 사용자가 다음처럼 입력하면,

```
오늘 저녁에 조용한 분위기에서 한식 먹을 사람 있어?
```

AI는 단순히 “한식”이라는 단어만 보는 것이 아니라, 문장 안에 들어 있는 여러 조건을 함께 고려해야 했다.

| 사용자 입력 요소 | AI가 해석해야 하는 의미 |
| --- | --- |
| 오늘 저녁 | 시간대 |
| 조용한 분위기 | 분위기 |
| 한식 | 메뉴 |
| 먹을 사람 | 같이 식사할 사람 찾기 |

이를 위해 매칭 AI는 RAG 검색을 사용했다.

사용자의 채팅 내용을 임베딩하고, 게시물 벡터 데이터와 비교해 의미적으로 가까운 게시물을 후보로 가져오는 방식이었다.

하지만 테스트 과정에서 추천 결과가 기대만큼 좋지 않았다. 사용자가 특정 메뉴나 분위기를 말했는데도 관련성이 낮은 게시물이 추천 후보로 포함되거나, 추천 결과가 사용자의 의도와 어긋나는 경우가 있었다.

처음에는 AI 프롬프트가 부족한지, 모델이 사용자 의도를 잘못 해석하는지, 혹은 게시물 데이터가 부족한지 의심했다. 하지만 설정을 확인해보니 실제 원인은 **RAG 검색에 사용되는 similarity threshold 값이 내가 설계한 값과 다르게 적용되고 있었던 것**이었다.

---

## 발생한 증상

| 증상 | 내용 |
| --- | --- |
| 관련성 낮은 게시물 추천 | 사용자 채팅과 의미적으로 거리가 있는 게시물이 후보에 포함됨 |
| 메뉴 조건 반영 부족 | 사용자가 특정 메뉴를 말해도 다른 메뉴 게시물이 추천됨 |
| 분위기 조건 반영 부족 | 조용함, 가벼움, 든든함 같은 분위기 조건이 약하게 반영됨 |
| 추천 결과 흔들림 | 같은 유형의 질문에서도 결과 품질이 일정하지 않음 |
| 원인 파악 어려움 | AI 응답 문제처럼 보였지만 실제로는 설정값 문제였음 |

특히 RAG 기반 추천에서는 AI가 최종 답변을 생성하기 전에 어떤 게시물을 후보로 가져오는지가 매우 중요하다.

후보 게시물이 부정확하면 AI가 아무리 자연스럽게 설명을 붙여도 최종 추천 품질은 떨어질 수밖에 없다.

즉, 이번 문제는 AI가 답변을 못 만든 문제가 아니라 **AI에게 전달되는 추천 후보군이 처음부터 기대와 다르게 구성된 문제**였다.

---

## 기존 설계

매칭 AI에서는 메뉴와 분위기 조건을 분리해서 threshold 값을 관리하려고 했다.

메뉴 조건은 비교적 명확하다.

예를 들어 한식, 중식, 일식, 치킨, 피자처럼 사용자가 원하는 음식 종류가 분명한 경우가 많다. 그래서 메뉴 유사도는 너무 낮게 잡으면 관련 없는 음식 게시물까지 후보로 들어올 수 있다.

반면 분위기 조건은 조금 더 추상적이다.

“조용한”, “가볍게”, “든든하게”, “편하게”, “빨리 먹을 수 있는” 같은 표현은 메뉴보다 의미가 넓기 때문에 너무 엄격하게 잡으면 후보가 거의 나오지 않을 수 있다.

그래서 다음처럼 메뉴와 분위기에 다른 threshold 값을 적용하려고 했다.

```
AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD=0.45
AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD=0.40
```

| 설정값 | 설계 의도 |
| --- | --- |
| `AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD=0.45` | 메뉴 관련성이 낮은 게시물을 줄이기 위해 기준을 조금 더 높게 설정 |
| `AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD=0.40` | 분위기 표현은 추상적이므로 약간 유연하게 검색되도록 설정 |

이 값들은 단순한 숫자가 아니라, 추천 후보를 얼마나 넓게 또는 좁게 가져올지 결정하는 기준이었다.

---

## 원인 분석

Spring Boot 설정에서는 `.env` 값을 통해 threshold를 주입받도록 구성되어 있었다.

```yaml
app:
  ai:
    matching:
      rag:
        top-k: ${AI_MATCHING_RAG_TOP_K}
        similarity-threshold: ${AI_MATCHING_RAG_SIMILARITY_THRESHOLD}
        menu-similarity-threshold: ${AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD}
        atmosphere-similarity-threshold: ${AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD}
```

운영 환경 설정에서도 같은 값을 참조하도록 되어 있었다.

```yaml
app:
  ai:
    matching:
      rag:
        similarity-threshold: ${AI_MATCHING_RAG_SIMILARITY_THRESHOLD}
        menu-similarity-threshold: ${AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD}
        atmosphere-similarity-threshold: ${AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD}
```

하지만 실제 실행 환경에서는 내가 설계한 threshold 값이 그대로 반영되지 않았다.

정확히 어떤 값으로 들어갔는지는 당시 로그로 남겨두지 않아 단정하기 어렵다. 그래서 문제를 기록할 때도 특정 잘못된 값을 적기보다는, **“내가 설계한 값과 실제 실행 환경에 적용된 값이 달랐다”**고 정리하는 것이 맞다고 판단했다.

중요한 것은 값이 `0.00`이었는지, 기본값이었는지가 아니라, 실행 중인 애플리케이션이 내가 의도한 메뉴/분위기 threshold 기준으로 RAG 검색을 수행하지 않았다는 점이다.

---

## 왜 이 문제가 추천 품질에 영향을 줬는가

RAG 검색에서 threshold는 후보 게시물을 걸러내는 필터 역할을 한다.

| threshold 상태 | 발생할 수 있는 문제 |
| --- | --- |
| 설계보다 낮게 적용됨 | 관련성이 낮은 게시물까지 후보로 포함됨 |
| 설계보다 높게 적용됨 | 추천 가능한 게시물이 지나치게 줄어듦 |
| 값이 누락되거나 다르게 적용됨 | 테스트 결과가 설계 의도와 달라짐 |
| 환경마다 값이 다름 | 로컬과 배포 환경의 추천 결과가 달라질 수 있음 |

예를 들어 메뉴 threshold를 `0.45`로 설계했는데 실제로는 그보다 낮게 적용되었다면, 사용자가 “한식”을 원한다고 말해도 한식과 관련성이 약한 게시물이 후보에 포함될 수 있다.

반대로 threshold가 너무 높게 적용되면, 실제로 추천할 만한 게시물도 후보에서 제외될 수 있다. 이 경우 AI는 충분한 후보를 받지 못해서 추천 결과가 빈약해질 수 있다.

즉, threshold 불일치는 두 가지 방향으로 모두 문제를 만들 수 있다.

```
기준이 너무 낮음 → 관련 없는 게시물이 추천 후보에 섞임
기준이 너무 높음 → 추천 가능한 게시물이 부족해짐
```

이번 문제에서는 사용자의 채팅과 관련성이 낮은 후보가 섞이면서, 최종 추천 결과가 기대보다 부정확하게 느껴졌다.

---

## 문제 흐름

전체 문제 흐름은 다음과 같았다.

| 단계 | 내용 |
| --- | --- |
| 1 | 메뉴/분위기 RAG 검색을 위해 threshold 값을 설계함 |
| 2 | `.env`에 `AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD`, `AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD` 값을 설정함 |
| 3 | Spring Boot 설정에서 해당 값을 참조하도록 구성함 |
| 4 | 실제 실행 환경에서 설계한 값과 다른 값이 적용됨 |
| 5 | RAG 검색 후보군이 예상과 다르게 구성됨 |
| 6 | 관련성이 낮은 게시물이 AI 추천 후보에 포함됨 |
| 7 | 최종 추천 결과가 사용자 채팅과 잘 맞지 않아 보임 |
| 8 | 원인을 확인한 결과 AI 로직보다 설정값 주입 문제가 핵심임을 파악함 |

---

## 기존 접근의 한계

처음에는 추천 결과가 좋지 않으니 AI 로직 자체를 먼저 의심했다.

| 처음 의심한 부분 | 실제 한계 |
| --- | --- |
| 프롬프트 문제 | 후보 게시물이 부정확하면 프롬프트만 고쳐도 한계가 있음 |
| 모델 응답 문제 | AI는 전달받은 후보 안에서 추천하기 때문에 검색 품질이 먼저 중요함 |
| 게시물 데이터 부족 | 데이터가 충분해도 threshold가 잘못되면 관련 없는 후보가 섞일 수 있음 |
| 추천 정렬 문제 | 후보군 생성 단계가 잘못되면 정렬 이전에 품질이 떨어짐 |

이번 문제를 통해 RAG 기반 기능은 단순히 “AI가 답변을 잘하느냐”만 보면 안 된다는 것을 알게 되었다.

RAG는 검색과 생성이 결합된 구조이기 때문에, 검색 단계에서 어떤 후보를 가져오는지가 최종 답변 품질을 크게 좌우한다.

---

## 해결 방법

먼저 `.env`와 Spring Boot 설정 파일을 다시 확인했다.

설정 파일에서는 다음 환경변수를 참조하고 있었다.

```yaml
menu-similarity-threshold: ${AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD}
atmosphere-similarity-threshold: ${AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD}
```

그래서 실행 환경에 내가 설계한 값이 명확하게 들어가도록 `.env` 설정을 정리했다.

```
AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD=0.45
AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD=0.40
```

또한 매칭 AI RAG 설정 전체가 함께 관리되도록 관련 값도 같이 확인했다.

```
AI_MATCHING_RAG_TOP_K=5
AI_MATCHING_RAG_SIMILARITY_THRESHOLD=0.65
AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD=0.45
AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD=0.40
```

이후 로컬과 배포 환경에서 같은 값이 적용되도록 `.env`, Docker compose, Spring profile 설정 흐름을 확인했다.

---

## 검증 방법

이 문제는 코드만 보고는 바로 알기 어려웠다.

설정 파일에는 환경변수 참조가 정상적으로 작성되어 있어도, 실제 실행 환경에서 값이 다르게 들어가면 문제가 발생하기 때문이다.

그래서 다음과 같은 방식으로 검증하는 것이 필요했다.

| 검증 항목 | 확인 내용 |
| --- | --- |
| `.env` 확인 | 설계한 threshold 값이 실제로 작성되어 있는지 확인 |
| Docker compose 확인 | `.env` 값이 애플리케이션 컨테이너에 전달되는지 확인 |
| Spring profile 확인 | 로컬/운영 프로필에서 같은 환경변수를 참조하는지 확인 |
| 설정 바인딩 확인 | `AiProperties`에 실제 값이 들어왔는지 확인 |
| 추천 결과 비교 | 같은 입력으로 threshold 정리 전후 추천 결과 비교 |

추가로 다음과 같이 실행 시 설정값을 로그로 남기면, 같은 문제가 반복될 때 더 빠르게 확인할 수 있다.

```java
log.info("AI Matching RAG menu threshold={}",
        aiProperties.getMatching().getRag().getMenuSimilarityThreshold());

log.info("AI Matching RAG atmosphere threshold={}",
        aiProperties.getMatching().getRag().getAtmosphereSimilarityThreshold());
```

기대하는 로그는 다음과 같다.

```
AI Matching RAG menu threshold=0.45
AI Matching RAG atmosphere threshold=0.4
```

이런 로그가 있으면 “설정 파일에는 있는데 실제 런타임에 반영됐는지”를 바로 확인할 수 있다.

---

## 개선 후 기대 동작

설정값이 정상적으로 반영되면, 매칭 AI는 사용자의 채팅 내용을 기준으로 더 적절한 후보 게시물을 가져올 수 있다.

예를 들어 사용자가 다음처럼 입력했다고 가정한다.

```
오늘 저녁에 조용한 곳에서 한식 먹고 싶어
```

개선 전에는 메뉴나 분위기 threshold가 설계와 다르게 적용되어 다음처럼 관련성이 낮은 후보가 섞일 수 있었다.

```
추천 후보:
- 마라탕 먹을 사람
- 디저트 카페 갈 사람
- 한식 백반 먹을 사람
- 치킨 먹을 사람
```

개선 후에는 메뉴와 분위기 기준이 더 일관되게 적용되어, 사용자 입력과 관련 있는 후보가 우선적으로 남게 된다.

```
추천 후보:
- 학교 근처 한식 백반 먹을 사람
- 조용한 식당에서 저녁 먹을 분
- 김치찌개 같이 먹을 사람
```

물론 추천 기능이 항상 완벽한 정답을 찾는 것은 아니다.

하지만 최소한 AI에게 전달되는 후보군이 사용자의 채팅 의도와 더 가까워지고, 최종 추천 설명도 자연스러워질 수 있다.

---

## 해결 결과

환경변수 설정을 정리한 뒤, 내가 설계한 메뉴/분위기 threshold 값이 매칭 AI RAG 검색에 반영되도록 개선했다.

그 결과 메뉴 조건과 분위기 조건이 추천 후보 검색에 더 일관되게 적용되었고, 관련성이 낮은 게시물이 후보에 포함되는 문제를 줄일 수 있었다.

또한 이번 문제를 통해 추천 품질 저하의 원인이 항상 AI 프롬프트나 모델 성능에 있는 것은 아니라는 점을 확인했다.

RAG 기반 추천에서는 모델이 답변을 생성하기 전에 검색 단계에서 어떤 후보를 가져오는지가 매우 중요하며, 이때 threshold 같은 설정값이 결과 품질에 직접적인 영향을 준다.

---

## 정리

이번 트러블슈팅은 AI 매칭 추천 품질이 낮아 보였던 원인을 설정값 주입 문제에서 찾은 사례이다.

초기에는 사용자의 채팅과 관련 없는 게시물이 추천되어 AI 로직이나 프롬프트 문제라고 생각했다. 하지만 확인 결과, `.env`에서 설계한 메뉴/분위기 RAG threshold 값이 실행 환경에서 다르게 적용되고 있었다.

정확히 어떤 값이 들어갔는지는 당시 기록이 부족해 단정하지 않았고, 대신 **“설계한 threshold 값과 실제 적용값이 달랐다”**는 점을 중심으로 정리했다.

이를 해결하기 위해 다음 값을 명확히 설정하고, Spring Boot 설정에서 정상적으로 참조되도록 정리했다.

```
AI_MATCHING_RAG_MENU_SIMILARITY_THRESHOLD=0.45
AI_MATCHING_RAG_ATMOSPHERE_SIMILARITY_THRESHOLD=0.40
```

결과적으로 매칭 AI가 사용자 채팅의 메뉴와 분위기 조건을 더 안정적으로 반영할 수 있게 되었고, RAG 검색 후보의 품질을 개선할 수 있었다.

이번 경험을 통해 AI 기능의 품질을 개선할 때는 프롬프트, 모델, 데이터뿐만 아니라 **환경변수 주입과 설정값 바인딩까지 함께 확인해야 한다**는 점을 배웠다.

---

</details>

---

<a id="postgresql-커넥션-초과로-인한-pgvector-저장-실패"></a>

<details>
<summary><strong>PostgreSQL 커넥션 초과로 인한 pgvector 저장 실패</strong></summary>

## PostgreSQL 커넥션 초과로 인한 pgvector 저장 실패

**문제 상황**

Docker Compose 환경에서 애플리케이션 실행 중 PostgreSQL 커넥션 초과 문제가 발생하였다.

게시글 벡터 저장 이벤트가 비동기로 실행되면서 pgvector 저장소에 동시에 여러 연결을 생성했고, PostgreSQL의 허용 커넥션 수를 초과하면서 벡터 upsert 작업이 실패하였다.

발생한 주요 에러는 다음과 같다.

```
org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection
```

실제 원인은 PostgreSQL 로그의 다음 메시지에서 확인할 수 있다.

```
Caused by: org.postgresql.util.PSQLException: FATAL: sorry, too many clients already
```

이는 PostgreSQL이 이미 너무 많은 클라이언트 연결을 사용 중이라 새로운 커넥션을 열 수 없다는 의미이다.

에러 발생 지점은 다음과 같다.

```
PostVectorRepository.upsertPost(PostVectorRepository.java:77)
PostVectorEventListener.handlePostVectorUpsert(PostVectorEventListener.java:32)
AsyncExecutionInterceptor
```

즉, 게시글 생성/수정 후 발생하는 `PostVectorUpsertEvent`를 비동기 이벤트 리스너가 처리하는 과정에서 PostgreSQL pgvector DB 연결이 과도하게 생성되며 문제가 발생하였다.

**원인 분석**

기존 RAG PostgreSQL 연결은 `DriverManagerDataSource` 기반으로 생성되고 있었다.

`DriverManagerDataSource`는 단순히 JDBC 연결을 생성하는 용도에 가깝고, 커넥션 풀을 통한 재사용이나 최대 연결 수 제한을 제공하지 않는다. 이 때문에 비동기 벡터 upsert 작업이나 RAG 검색 요청이 겹치면 PostgreSQL에 새로운 연결이 계속 생성될 수 있었다.

특히 매칭 AI의 게시글 벡터 저장은 비동기로 처리되기 때문에 짧은 시간에 여러 게시글 벡터 upsert가 발생하면 PostgreSQL 연결 수가 급격히 증가할 수 있다.

문제 흐름은 다음과 같다.

```
게시글 생성 또는 수정
↓
PostVectorUpsertEvent 발행
↓
PostVectorEventListener 비동기 실행
↓
PostVectorRepository.upsertPost()
↓
pgvector PostgreSQL 연결 생성
↓
동시 이벤트 증가
↓
PostgreSQL max_connections 초과
↓
too many clients already 발생
```

**해결 방법**

RAG/pgvector 전용 PostgreSQL 연결을 `DriverManagerDataSource`에서 `HikariDataSource` 기반 커넥션 풀로 변경하였다.

또한 RAG PostgreSQL DataSource를 별도 Bean으로 분리하고, `JdbcTemplate`과 `PgVectorStore`가 같은 커넥션 풀을 재사용하도록 수정하였다.

```
aiRagDataSource
↓
aiRagJdbcTemplate
↓
PgVectorStore / PostVectorRepository
↓
PostgreSQL pgvector
```

추가로 RAG PostgreSQL DataSource가 메인 MySQL DataSource로 잘못 선택되지 않도록 `defaultCandidate = false`를 지정하였다.

**추가 설정**

커넥션 풀 크기와 타임아웃을 환경변수로 조정할 수 있도록 다음 설정을 추가하였다.

```
AI_RAG_POSTGRES_MAXIMUM_POOL_SIZE=5
AI_RAG_POSTGRES_MINIMUM_IDLE=1
AI_RAG_POSTGRES_CONNECTION_TIMEOUT_MS=30000
AI_RAG_POSTGRES_IDLE_TIMEOUT_MS=30000
AI_RAG_POSTGRES_MAX_LIFETIME_MS=1800000
```

각 설정의 의미는 다음과 같다.

| 설정 | 의미 |
| --- | --- |
| `AI_RAG_POSTGRES_MAXIMUM_POOL_SIZE` | RAG PostgreSQL에 동시에 열 수 있는 최대 커넥션 수 |
| `AI_RAG_POSTGRES_MINIMUM_IDLE` | 평소 유지할 최소 유휴 커넥션 수 |
| `AI_RAG_POSTGRES_CONNECTION_TIMEOUT_MS` | 커넥션을 얻기 위해 대기할 최대 시간 |
| `AI_RAG_POSTGRES_IDLE_TIMEOUT_MS` | 사용하지 않는 커넥션을 정리하기까지의 시간 |
| `AI_RAG_POSTGRES_MAX_LIFETIME_MS` | 커넥션 하나를 유지할 수 있는 최대 수명 |

현재 설정에서는 RAG PostgreSQL 커넥션을 최대 5개까지만 열 수 있도록 제한한다. 따라서 비동기 벡터 upsert 이벤트가 동시에 많이 발생해도 PostgreSQL에 무제한으로 연결을 생성하지 않고, 커넥션 풀 범위 안에서 대기하거나 재사용하게 된다.

**개선 효과**

이번 변경으로 다음과 같은 효과를 얻었다.

| 개선 항목 | 설명 |
| --- | --- |
| 커넥션 초과 방지 | PostgreSQL에 생성되는 연결 수를 `maximumPoolSize` 이하로 제한 |
| 연결 재사용 | 매 요청마다 새 연결을 만들지 않고 기존 커넥션 재사용 |
| 비동기 이벤트 안정화 | 게시글 벡터 upsert 이벤트가 동시에 발생해도 커넥션 풀에서 제어 |
| 운영 설정 분리 | 풀 크기와 타임아웃을 환경변수로 조정 가능 |
| MySQL과 분리 | RAG PostgreSQL DataSource를 메인 MySQL DataSource와 분리 |

**정리**

이번 장애는 매칭 AI의 게시글 벡터 upsert 이벤트가 비동기로 실행되면서 PostgreSQL pgvector 연결이 과도하게 생성되어 발생한 문제였다.

이를 해결하기 위해 RAG/pgvector 전용 PostgreSQL 연결을 HikariCP 기반 커넥션 풀로 변경하고, 최대 커넥션 수와 타임아웃을 환경변수로 관리하도록 수정하였다.

그 결과 PostgreSQL 연결 수를 제한하고 재사용할 수 있게 되어, Docker Compose 환경에서도 pgvector 기반 게시글 벡터 저장과 RAG 검색이 더 안정적으로 동작하도록 개선하였다.

</details>

---

<a id="grafana-alert-rule을-ui가-아닌-yaml-provisioning으로-관리한-이유"></a>

<details>
<summary><strong>Grafana Alert Rule을 UI가 아닌 YAML Provisioning으로 관리한 이유</strong></summary>

## **Grafana Alert Rule을 UI가 아닌 YAML Provisioning으로 관리한 이유**

문제 상황

모니터링 시스템을 구축하면서 Grafana, Prometheus, Loki, Alloy, n8n, Slack을 연동해 장애 알림 흐름을 만들었다.

초기에는 Grafana UI에서 직접 Alert Rule과 Contact Point를 만들면서 테스트했다. UI 방식은 빠르게 알림 조건을 만들고 수정할 수 있어서 로컬 테스트에는 편했다. 하지만 EC2 배포 환경까지 고려했을 때, Grafana 설정을 UI에만 의존하면 몇 가지 문제가 있었다.

가장 큰 고민은 **Grafana Alert Rule을 미리 YAML 파일로 만들어서 배포 시 자동으로 뿌려줄 것인지**, 아니면 **Grafana UI에서 직접 만들고 Docker volume으로 유지할 것인지**였다.

처음에는 UI에서 직접 만드는 방식이 더 단순해 보였다. 하지만 운영 환경에서는 컨테이너를 새로 올리거나, 다른 서버에 배포하거나, 설정을 다시 재현해야 하는 상황이 생길 수 있었다. 이때 Alert Rule이 Grafana 내부 DB에만 저장되어 있으면 어떤 알림이 설정되어 있는지 코드로 추적하기 어렵고, 실수로 설정이 빠질 가능성도 있었다.

---

## 고민한 방식 비교

| 방식 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| Grafana UI에서 직접 생성 | 빠르게 만들고 테스트 가능 | 설정 이력이 코드에 남지 않음 | 로컬 테스트에는 적합 |
| Docker volume으로 Grafana 설정 유지 | 컨테이너 재시작 후에도 설정 유지 | 새 환경에서 동일 설정 재현이 어려움 | 보조 수단으로 사용 |
| YAML provisioning으로 Alert Rule 관리 | 설정을 코드로 관리 가능, 배포 시 자동 생성 | 처음 작성이 번거롭고 문법 오류 가능 | 운영 Alert 관리에 적합 |
| 모든 대시보드까지 YAML로 관리 | 완전한 IaC 가능 | 대시보드 수정이 불편함 | 이번 프로젝트에는 과함 |

결과적으로 **대시보드는 Grafana UI에서 직접 커스텀하고**, **Alert Rule과 Contact Point는 YAML provisioning으로 관리**하는 방식으로 결정했다.

대시보드는 시각적으로 자주 수정할 수 있으므로 UI 방식이 더 효율적이었다. 반면 Alert Rule은 장애 감지 기준이기 때문에 누락되면 운영 안정성에 직접 영향을 줄 수 있어 코드로 관리하는 것이 더 적합하다고 판단했다.

---

## 원인 분석

Grafana 설정을 UI에만 의존하면 다음과 같은 문제가 발생할 수 있었다.

| 문제 | 설명 |
| --- | --- |
| 설정 재현 어려움 | EC2나 다른 환경에서 동일한 Alert Rule을 다시 수동으로 만들어야 함 |
| 운영 실수 가능성 | Alert Rule, Contact Point, Label, Annotation을 빠뜨릴 수 있음 |
| 변경 이력 추적 어려움 | 어떤 알림 조건이 언제 바뀌었는지 Git으로 확인하기 어려움 |
| 협업 불편 | 팀원이 Grafana UI에 직접 들어가야만 설정 내용을 확인할 수 있음 |
| 배포 자동화 한계 | Docker compose로 서비스를 올려도 Alert 설정은 별도 수동 작업이 필요함 |

특히 이번 프로젝트에서는 Kafka DLQ/DLT, Spring ERROR 로그, API 5xx, AI 토큰 사용량, DB 커넥션 대기 등 여러 Alert Rule이 필요했다. 이런 알림들을 매번 UI에서 직접 만드는 것은 반복 작업이 많고, 배포 환경에서 누락될 위험이 있었다.

---

## 해결 방향

Grafana의 provisioning 기능을 사용해 Alert Rule과 Contact Point를 YAML 파일로 관리했다.

프로젝트에서는 다음과 같은 방향으로 정리했다.

| 항목 | 관리 방식 |
| --- | --- |
| Grafana datasource | YAML provisioning |
| Prometheus datasource | 자동 등록 |
| Loki datasource | 자동 등록 |
| Grafana dashboard | UI에서 직접 생성 및 Docker volume으로 유지 |
| Grafana Alert Rule | YAML provisioning |
| Contact Point | YAML provisioning |
| n8n workflow | n8n에서 import 후 Active 상태 유지 |
| Slack Webhook URL | 환경변수로 관리 |

이렇게 분리한 이유는 관리 대상의 성격이 달랐기 때문이다.

대시보드는 시각적인 요소가 많아서 UI에서 수정하는 것이 편했다. 반면 Alert Rule은 장애 감지 조건이므로 운영 환경에서 항상 동일하게 생성되어야 했다. 따라서 Alert Rule은 YAML로 코드화하는 것이 더 안정적이었다.

---

## 적용 예시

Grafana provisioning 디렉터리를 따로 두고, Docker compose에서 Grafana 컨테이너가 해당 설정을 읽도록 구성했다.

예시 구조는 다음과 같다.

```yaml
monitoring
  prometheus.yml
  loki-config.yml
  alloy-config.alloy
  grafana/
    provisioning/
      datasources/
        datasources.yml
      alerting/
        contact-points.yml
        alert-rules.yml
```

Grafana 컨테이너에서는 provisioning 디렉터리를 마운트해 실행 시 datasource와 alert rule이 자동 등록되도록 했다.

```yaml
grafana:
  image: grafana/grafana
  container_name: grafana
  ports:
    - "3000:3000"
  volumes:
    - grafana-data:/var/lib/grafana
    - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
  environment:
    - GF_SECURITY_ADMIN_USER=admin
    - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}
  depends_on:
    - prometheus
    - loki
```

위 설정을 통해 Grafana 컨테이너가 실행될 때 `/etc/grafana/provisioning` 아래의 설정 파일을 읽고, datasource와 alert 설정을 자동으로 생성하게 했다.

---

## Datasource Provisioning 예시

Prometheus와 Loki를 Grafana에서 수동으로 등록하지 않고, YAML 파일로 자동 등록했다.

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
```

이 설정 덕분에 EC2에서 Docker compose를 다시 실행해도 Grafana에 접속해서 datasource를 다시 등록할 필요가 없었다.

---

## Alert Rule Provisioning 예시

Spring Boot 애플리케이션이 Prometheus scrape 대상에서 사라졌을 때 감지하는 Application DOWN 알림을 YAML로 관리했다.

```yaml
apiVersion: 1

groups:
  - orgId: 1
    name: application-alerts
    folder: Application
    interval: 1m
    rules:
      - uid: application-down
        title: Application DOWN
        condition: C
        for: 1m
        labels:
          severity: critical
          domain: application
          service: hankkipot-api
          env: prod
          team: backend
          priority: P1
        annotations:
          summary: Spring Boot application is down
          description: Prometheus cannot scrape the Spring Boot application.
          impact: API 요청 처리가 불가능할 수 있습니다.
          action: 애플리케이션 컨테이너 상태와 Spring Boot 로그를 확인하세요.
        data:
          - refId: A
            datasourceUid: prometheus
            relativeTimeRange:
              from: 300
              to: 0
            model:
              expr: up{job="spring-boot-app"}
              refId: A
          - refId: C
            datasourceUid: __expr__
            model:
              type: threshold
              expression: A
              conditions:
                - evaluator:
                    type: lt
                    params:
                      - 1
```

이때 Alert Rule에 `severity`, `domain`, `service`, `env`, `team`, `priority` 같은 label을 함께 넣어두었다.

이 값들은 이후 n8n에서 Slack 메시지를 구성하거나 Gemini 분석 프롬프트를 만들 때 활용할 수 있었다.

---

## Contact Point Provisioning 예시

Grafana Alert가 발생했을 때 n8n Webhook으로 전달되도록 Contact Point도 YAML로 관리했다.

```yaml
apiVersion: 1

contactPoints:
  - orgId: 1
    name: n8n-webhook
    receivers:
      - uid: n8n-webhook
        type: webhook
        settings:
          url: http://n8n:5678/webhook/grafana-alert
          httpMethod: POST
```

Grafana가 직접 Slack으로 보내는 구조가 아니라, n8n을 중간에 둔 이유는 단순 알림보다 더 많은 처리를 하기 위해서였다.

n8n에서는 Grafana Alert payload를 받아서 Loki 로그를 조회하고, Gemini로 원인 분석을 수행한 뒤 Slack 메시지로 포맷팅했다.

---

## 최종 알림 흐름

| 단계 | 흐름 | 역할 |
| --- | --- | --- |
| 1 | Spring Boot / Kafka 로그 발생 | 장애 또는 에러 로그 생성 |
| 2 | Alloy → Loki | 컨테이너 로그 및 Kafka 관련 로그 수집 |
| 3 | Prometheus | Spring Actuator 메트릭 수집 |
| 4 | Grafana Alert Rule | Prometheus/Loki 조건 기반 장애 감지 |
| 5 | Grafana Contact Point | n8n Webhook으로 Alert payload 전달 |
| 6 | n8n | Alert 파싱, Loki context 조회, Gemini 분석 |
| 7 | Slack | 분석 결과와 조치 내용을 알림으로 전송 |

---

## 해결 결과

YAML provisioning을 적용한 뒤에는 Grafana Alert Rule과 Contact Point를 배포 시 자동으로 생성할 수 있게 되었다.

그 결과 EC2 환경에서 컨테이너를 다시 올려도 Alert Rule을 수동으로 다시 만들 필요가 없어졌고, 어떤 알림 조건이 운영 환경에 적용되는지 Git으로 확인할 수 있게 되었다.

또한 Alert Rule에 label과 annotation을 명확히 정의하면서 n8n workflow에서도 알림의 성격을 더 정확하게 파악할 수 있었다. 예를 들어 `domain=ai`, `severity=warning`, `service=hankkipot-api` 같은 값이 포함되면 Slack 메시지에서도 어떤 도메인의 어떤 수준 장애인지 바로 확인할 수 있었다.

---

## 정리

이번 문제는 단순히 Grafana Alert를 만드는 문제가 아니라, **운영 환경에서 알림 설정을 어떻게 안정적으로 재현하고 관리할 것인가**에 대한 고민이었다.

처음에는 UI에서 직접 만드는 방식이 빠르고 편했지만, 배포 환경과 협업을 고려하면 Alert Rule은 코드로 관리하는 것이 더 적합했다. 그래서 Grafana provisioning을 적용해 datasource, Alert Rule, Contact Point를 YAML로 관리했고, 대시보드는 UI에서 직접 커스텀하는 방식으로 역할을 나누었다.

이를 통해 모니터링 설정의 재현성을 높이고, EC2 배포 환경에서도 동일한 알림 정책을 유지할 수 있었다.

---

</details>

---

<a id="n8n-설정-오류로-slack-알림이-정상-전달되지-않은-문제"></a>

<details>
<summary><strong>n8n 설정 오류로 Slack 알림이 정상 전달되지 않은 문제</strong></summary>

## n8n 설정 오류로 Slack 알림이 정상 전달되지 않은 문제

## 문제 상황

Grafana Alert를 Slack으로 바로 보내는 단순 구조가 아니라, 중간에 n8n을 두고 알림을 가공하는 구조를 구성했다.

구성하려던 최종 흐름은 다음과 같았다.

| 단계 | 흐름 | 역할 |
| --- | --- | --- |
| 1 | Grafana Alert 발생 | Prometheus 또는 Loki 기반 Alert Rule firing |
| 2 | Grafana Contact Point | n8n Webhook으로 Alert payload 전송 |
| 3 | n8n Webhook | Grafana Alert payload 수신 |
| 4 | Parse Grafana Alert | alert name, severity, domain 등 필요한 값 추출 |
| 5 | Store Pending Alert | 정기 요약 전송을 위해 알림 임시 저장 |
| 6 | Digest Schedule | 오전/오후 정해진 시간에 pendingAlerts 요약 |
| 7 | Gemini 분석 | 누적된 Alert와 Loki context를 기반으로 원인 분석 |
| 8 | Slack 전송 | 분석 결과를 Slack 채널로 전송 |
| 9 | Clear Sent Alerts | 전송 완료된 알림을 pendingAlerts에서 제거 |

처음에는 Grafana에서 Contact Point Test를 실행하면 n8n Webhook까지는 요청이 들어오는 것처럼 보였다. 하지만 Slack 채널에는 기대한 형태의 알림이 오지 않거나, 정기 요약 시점에 `Grafana Alert #1`, `unknown`, `unknown` 같은 의미 없는 알림이 계속 포함되는 문제가 발생했다.

처음에는 Gemini API 요청 Body나 분석 응답 문제가 원인이라고 생각했다. 하지만 로그와 n8n 실행 결과를 따라가 보니 핵심 원인은 Gemini가 아니라 **n8n workflow 설정과 Grafana 테스트 payload 처리 방식**이었다.

---

## 발생한 증상

| 증상 | 내용 |
| --- | --- |
| Slack 알림 미수신 | n8n workflow는 실행되지만 Slack 채널에 알림이 오지 않음 |
| 의미 없는 알림 누적 | `Grafana Alert #1`, `severity=unknown`, `domain=unknown` 데이터가 pendingAlerts에 계속 저장됨 |
| 정기 요약 품질 저하 | Gemini가 실제 장애가 아닌 테스트성 알림을 분석 대상으로 사용함 |
| 원인 파악 어려움 | Grafana Contact Point Test는 성공처럼 보였지만 실제 payload가 부실했음 |
| 운영 환경 설정 불안정 | Slack Webhook URL이 환경변수와 workflow 설정에서 일치하지 않을 가능성이 있었음 |

특히 가장 헷갈렸던 부분은 Grafana에서는 Contact Point Test가 성공했다고 나오는데, 실제 Slack 알림은 기대한 형태로 오지 않는 점이었다.

Grafana의 테스트 성공은 “Webhook 요청을 보냈다”는 의미에 가까웠고, n8n 내부에서 해당 payload를 정상 알림으로 해석했는지, Slack URL이 올바른지, 저장된 pendingAlerts가 정상인지까지 보장하지는 않았다.

---

## 원인 분석

문제를 나눠서 보면 크게 두 가지 원인이 있었다.

## 1. Slack Webhook URL 및 n8n 환경변수 설정 문제

Slack 알림을 보내기 위해 n8n에서는 Slack Incoming Webhook URL을 사용했다.

초기에는 이 URL이 workflow 내부에 직접 들어가 있거나, 환경변수로 관리하려는 설정과 실제 n8n 컨테이너 설정이 맞지 않는 부분이 있었다.

운영 환경에서는 Slack Webhook URL 같은 민감한 값이 코드나 workflow JSON에 직접 들어가면 보안상 좋지 않고, 배포 환경에서 URL이 누락되면 Slack 전송 노드가 실패할 수 있다.

예를 들어 Slack 전송 노드가 아래처럼 하드코딩된 URL을 사용하면 로컬에서는 동작하더라도, EC2나 다른 환경에서는 URL 변경 시 workflow를 다시 수정해야 한다.

```
https://hooks.slack.com/services/...
```

그래서 Slack URL은 환경변수로 관리하는 방식이 더 적합했다.

```
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
```

n8n workflow에서는 다음처럼 환경변수를 참조하도록 정리할 수 있다.

```
={{ $env.SLACK_WEBHOOK_URL }}
```

이렇게 하면 Docker compose나 EC2의 `.env`에서 값만 관리하면 되고, workflow 자체에는 민감한 Slack URL을 남기지 않을 수 있다.

---

## 2. Grafana Contact Point Test payload가 실제 Alert payload와 달랐던 문제

두 번째 원인이 더 핵심이었다.

Grafana의 Contact Point Test는 실제 Alert Rule에서 발생한 payload와 동일하지 않았다. 테스트 payload는 `labels`, `annotations` 정보가 부족할 수 있었고, n8n의 Parse Grafana Alert 노드에서는 필요한 값을 찾지 못하면 fallback 값을 넣도록 되어 있었다.

예를 들어 n8n에서 다음 값을 기대하고 있었다.

| 기대한 값 | 용도 |
| --- | --- |
| `labels.alertname` | 알림 이름 |
| `labels.severity` | 장애 심각도 |
| `labels.domain` | 장애 도메인 |
| `labels.service` | 영향받는 서비스 |
| `annotations.summary` | 알림 요약 |
| `annotations.description` | 상세 설명 |
| `annotations.action` | 조치 방법 |

하지만 Grafana Contact Point Test payload에는 이런 값이 충분히 들어오지 않았다.

그 결과 Parse Grafana Alert 단계에서 다음과 같은 fallback 값이 만들어졌다.

```
alertName: Grafana Alert #1
severity: unknown
domain: unknown
```

문제는 여기서 끝나지 않았다. Store Pending Alert 노드가 이 알림을 검증 없이 그대로 `pendingAlerts`에 저장하고 있었다.

즉, 실제 문제 흐름은 다음과 같았다.

| 순서 | 문제 흐름 |
| --- | --- |
| 1 | Grafana Contact Point Test 실행 |
| 2 | labels, annotations가 부족한 테스트 payload가 n8n으로 들어옴 |
| 3 | Parse Grafana Alert가 fallback 값 생성 |
| 4 | `Grafana Alert #1 / unknown / unknown` 알림 생성 |
| 5 | Store Pending Alert가 검증 없이 pendingAlerts에 저장 |
| 6 | 정기 요약 시간에 Build Digest Prompt가 의미 없는 알림까지 Gemini에 전달 |
| 7 | Slack으로 실제 장애가 아닌 테스트성 알림 요약이 전송되거나, 기대한 알림이 오지 않음 |

처음에는 Gemini 분석 결과가 이상하다고 생각했지만, 실제로는 Gemini로 넘어가기 전 데이터가 이미 잘못되어 있었다.

---

## 기존 Store Pending Alert의 문제

기존 Store Pending Alert는 들어온 알림이 `firing` 상태인지 정도만 확인하고, 알림 내용이 실제로 의미 있는지까지는 검사하지 않았다.

특히 다음과 같은 알림도 저장될 수 있었다.

```json
{
  "alertName": "Grafana Alert #1",
  "severity": "unknown",
  "domain": "unknown",
  "labels": {
    "alertname": "Grafana Alert #1"
  },
  "annotations": {}
}
```

이 데이터는 운영 알림으로 볼 수 없었다.

하지만 `pendingAlerts`에 저장되면 정기 요약 대상이 되기 때문에 Slack 알림 품질을 떨어뜨렸다.

---

## 해결 방향

해결은 크게 네 단계로 진행했다.

| 단계 | 해결 내용 |
| --- | --- |
| 1 | Slack Webhook URL을 환경변수 기반으로 정리 |
| 2 | Store Pending Alert에서 의미 없는 fallback 알림 저장 차단 |
| 3 | 이미 쌓인 pendingAlerts 데이터 정리 |
| 4 | Grafana Contact Point Test 대신 실제 labels/annotations가 포함된 payload로 테스트 |

---

## 1. Slack Webhook URL 환경변수 정리

Slack Incoming Webhook URL은 workflow에 직접 넣지 않고 환경변수로 관리하도록 정리했다.

Docker compose에서는 n8n 서비스에 환경변수를 전달한다.

```yaml
n8n:
  image: n8nio/n8n
  container_name: n8n
  ports:
    - "5678:5678"
  environment:
    - N8N_HOST=localhost
    - N8N_PORT=5678
    - N8N_PROTOCOL=http
    - SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL}
    - GEMINI_API_KEY=${GEMINI_API_KEY}
  volumes:
    - n8n-data:/home/node/.n8n
```

그리고 `.env` 또는 EC2 환경변수에는 다음처럼 값을 둔다.

```
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
GEMINI_API_KEY=...
```

n8n의 Slack 전송 노드에서는 URL을 직접 쓰지 않고 다음 표현식을 사용한다.

```
={{ $env.SLACK_WEBHOOK_URL }}
```

이렇게 하면 로컬, Docker, EC2 환경에서 같은 workflow를 사용하면서도 민감한 값은 환경별로 분리해서 관리할 수 있다.

---

## 2. 의미 없는 Grafana 테스트 알림 저장 차단

Store Pending Alert 노드에 fallback 알림을 걸러내는 로직을 추가했다.

핵심은 단순히 `labels.alertname` 키가 있는지만 보는 것이 아니라, 값이 실제 운영 알림으로 의미 있는지 확인하는 것이었다.

```jsx
function isMeaninglessFallbackAlert(alert) {
  if (!alert || typeof alert !== 'object') {
    return true;
  }

  const alertName = String(alert.alertName || '').trim();
  const severity = String(alert.severity || '').trim().toLowerCase();
  const domain = String(alert.domain || '').trim().toLowerCase();

  const labels = alert.labels || {};
  const annotations = alert.annotations || {};

  const labelAlertName = String(labels.alertname || '').trim();
  const summary = String(alert.summary || annotations.summary || '').trim();
  const description = String(alert.description || annotations.description || '').trim();

  const isFallbackName =
    alertName.startsWith('Grafana Alert #') ||
    alertName === 'Grafana Alert' ||
    alertName === 'N/A' ||
    alertName === '' ||
    labelAlertName.startsWith('Grafana Alert #');

  const isUnknownSeverity =
    !severity || severity === 'unknown';

  const isUnknownDomain =
    !domain || domain === 'unknown';

  const hasRealSeverity =
    !!labels.severity &&
    String(labels.severity).toLowerCase() !== 'unknown';

  const hasRealDomain =
    !!labels.domain &&
    String(labels.domain).toLowerCase() !== 'unknown';

  const hasRealService =
    !!labels.service || !!labels.job || !!labels.app;

  const hasRealSummary =
    summary &&
    !summary.startsWith('Grafana Alert #') &&
    summary !== 'Grafana Alert';

  const hasRealDescription =
    description &&
    !description.startsWith('Grafana Alert #') &&
    description !== 'Grafana Alert';

  const hasRealAction =
    !!annotations.impact ||
    !!annotations.action ||
    !!annotations.runbook_url;

  const hasMeaningfulInfo =
    hasRealSeverity ||
    hasRealDomain ||
    hasRealService ||
    hasRealSummary ||
    hasRealDescription ||
    hasRealAction;

  return isFallbackName && isUnknownSeverity && isUnknownDomain && !hasMeaningfulInfo;
}
```

이 함수는 아래와 같은 알림을 의미 없는 알림으로 판단한다.

| 조건 | 판단 |
| --- | --- |
| alertName이 `Grafana Alert #1` | 테스트성 fallback 가능성 높음 |
| severity가 `unknown` | 운영 알림 심각도 없음 |
| domain이 `unknown` | 장애 도메인 없음 |
| summary/description/action 없음 | 조치 가능한 정보 없음 |
| service/job/app 없음 | 영향받는 대상 없음 |

그 후 Store Pending Alert에서 다음처럼 필터링했다.

```jsx
const validAlerts = incomingAlerts.filter(alert => {
  if (!alert || typeof alert !== 'object') {
    return false;
  }

  if (String(alert.status || '').toLowerCase() !== 'firing') {
    return false;
  }

  if (isMeaninglessFallbackAlert(alert)) {
    return false;
  }

  return true;
});
```

이렇게 수정한 뒤에는 `Grafana Alert #1 / unknown / unknown` 같은 알림이 들어와도 `pendingAlerts`에 저장되지 않는다.

---

## 3. 이미 쌓인 pendingAlerts 정리

저장 차단 로직을 추가해도 이미 `pendingAlerts`에 들어간 데이터는 남아 있었다.

당시에는 의미 없는 unknown 알림이 여러 개 쌓여 있었기 때문에, 정기 요약 전에 한 번 정리할 필요가 있었다.

이를 위해 Cleanup Pending Alerts 노드를 추가했다.

```jsx
const data = $getWorkflowStaticData('global');

const alerts = Array.isArray(data.pendingAlerts)
  ? data.pendingAlerts
  : [];

const beforeCount = alerts.length;

data.pendingAlerts = alerts.filter(alert => {
  if (isMeaninglessFallbackAlert(alert)) {
    return false;
  }

  return true;
});

return [
  {
    json: {
      status: 'cleanup_done',
      beforeCount,
      afterCount: data.pendingAlerts.length,
      removedCount: beforeCount - data.pendingAlerts.length,
    },
  },
];
```

정리 후에는 다음과 같은 결과를 기대할 수 있었다.

```json
{
  "status": "cleanup_done",
  "beforeCount": 56,
  "afterCount": 0,
  "removedCount": 56
}
```

이 작업으로 이미 쌓여 있던 테스트성 알림을 제거하고, 이후 정기 요약에는 실제 운영 알림만 포함되도록 만들었다.

---

## 4. 실제 Alert payload로 테스트 방식 변경

Grafana Contact Point Test만으로는 실제 알림 흐름을 검증하기 부족했다.

그래서 `labels`와 `annotations`가 포함된 실제 payload를 직접 n8n Webhook으로 보내는 방식으로 테스트했다.

```bash
docker exec -it grafana curl -X POST http://n8n:5678/webhook/grafana-alert \
  -H "Content-Type: application/json" \
  -d '{
    "status": "firing",
    "alerts": [
      {
        "status": "firing",
        "labels": {
          "alertname": "AIAPI5xxDetected",
          "severity": "warning",
          "domain": "ai",
          "source": "prometheus",
          "service": "hankkipot-api",
          "env": "prod",
          "team": "backend",
          "priority": "P2"
        },
        "annotations": {
          "summary": "AI API 5xx response detected",
          "description": "AI related endpoint returned at least one 5xx response in the last 5 minutes.",
          "impact": "AI 관련 기능에서 서버 오류가 발생했습니다.",
          "action": "AI API uri, Spring exception 로그, 외부 AI API 응답을 확인하세요.",
          "runbook_url": "https://example.com/runbook/ai-api-5xx"
        }
      }
    ]
  }'
```

이 payload로 테스트하면 n8n이 실제 운영 알림과 비슷한 데이터를 받아 처리할 수 있었다.

기대 결과는 다음과 같았다.

```json
{
  "status": "stored",
  "receivedAlerts": 1,
  "storedAlerts": 1,
  "skipped": 0,
  "created": 1,
  "pendingCount": 1,
  "alertNames": ["AIAPI5xxDetected"]
}
```

반대로 의미 없는 테스트 알림이 들어오면 다음처럼 저장되지 않아야 했다.

```json
{
  "status": "stored",
  "receivedAlerts": 1,
  "storedAlerts": 0,
  "skipped": 1,
  "skippedReason": "meaningless fallback alerts were ignored"
}
```

---

## 수정 후 n8n Workflow 구조

최종적으로 n8n workflow는 다음과 같이 정리했다.

| 순서 | 노드 | 역할 |
| --- | --- | --- |
| 1 | Grafana Alert Webhook | Grafana Alert payload 수신 |
| 2 | Parse Grafana Alert | alert name, status, severity, domain, labels, annotations 추출 |
| 3 | Is Firing? | firing 상태인 알림만 처리 |
| 4 | Store Pending Alert | 유효한 알림만 pendingAlerts에 저장 |
| 5 | Digest Schedule 10 and 20 KST | 정해진 시간에 요약 workflow 실행 |
| 6 | Cleanup Pending Alerts | 의미 없는 알림 및 오래된 알림 정리 |
| 7 | Build Digest Prompt | pendingAlerts 기반 Gemini 프롬프트 생성 |
| 8 | Has Alerts? | 요약할 알림이 있는지 확인 |
| 9 | Analyze Digest with Gemini | Gemini로 원인/영향/조치 분석 |
| 10 | Format Digest Slack Message | Slack 메시지 형태로 변환 |
| 11 | Send Digest to Slack | Slack Incoming Webhook으로 전송 |
| 12 | Clear Sent Alerts | 전송 완료된 알림 제거 |

---

## 해결 결과

수정 후에는 n8n workflow가 의미 없는 Grafana 테스트 알림을 저장하지 않게 되었다.

또한 이미 쌓여 있던 `Grafana Alert #1 / unknown / unknown` 데이터도 Cleanup Pending Alerts 단계에서 제거되었다.

Slack Webhook URL도 환경변수로 관리하도록 정리하면서, 로컬과 EC2 환경에서 같은 workflow를 사용할 수 있게 되었다.

그 결과 Slack 알림이 특정 환경에서만 실패하거나, workflow JSON에 민감한 Webhook URL이 남는 문제를 줄일 수 있었다.

최종적으로 Grafana Alert가 발생하면 n8n이 실제 알림 정보만 저장하고, 정기 요약 시 Gemini 분석 결과를 Slack으로 전달하는 구조가 안정화되었다.

---

## 배운 점

이번 문제에서 중요한 점은 Slack 알림이 오지 않는다고 해서 바로 Slack이나 Gemini만 의심하면 안 된다는 것이었다.

실제로는 알림 전송 흐름 중간에 있는 n8n workflow에서 데이터가 어떻게 파싱되고 저장되는지 확인해야 했다. 특히 Grafana Contact Point Test는 실제 Alert Rule payload와 다를 수 있기 때문에, 테스트 성공 여부만 보고 전체 알림 흐름이 정상이라고 판단하면 안 됐다.

이번 트러블슈팅을 통해 알림 시스템에서는 다음 세 가지가 중요하다는 것을 확인했다.

| 항목 | 배운 점 |
| --- | --- |
| 환경변수 관리 | Slack Webhook URL, Gemini API Key 같은 값은 환경변수로 분리해야 함 |
| payload 검증 | 외부 시스템에서 들어온 알림은 저장 전에 유효성 검사를 해야 함 |
| 테스트 방식 | Grafana Contact Point Test뿐 아니라 실제 labels/annotations가 포함된 payload로 검증해야 함 |

결과적으로 단순히 “Slack 알림이 안 왔다”는 문제를 해결한 것이 아니라, n8n 기반 알림 파이프라인에서 잘못된 데이터가 저장되고 요약되는 구조까지 함께 개선할 수 있었다.

---

</details>

---

<a id="무중단-배포-환경에서-모니터링알림-설정이-단일-서버-기준으로-되어-있던-문제"></a>

<details>
<summary><strong>무중단 배포 환경에서 모니터링/알림 설정이 단일 서버 기준으로 되어 있던 문제</strong></summary>

## 무중단 배포 환경에서 모니터링/알림 설정이 단일 서버 기준으로 되어 있던 문제

## 문제 상황

Grafana, Prometheus, Loki, Alloy, n8n을 이용해 배포 환경 모니터링과 Slack 알림 흐름을 구성했다.

처음에는 Spring Boot 애플리케이션이 하나의 컨테이너로 실행되는 구조를 기준으로 모니터링을 설정했다. 로컬 Docker 환경에서는 앱 컨테이너 이름이 `hankkipot-app` 하나였기 때문에, Prometheus와 Loki 설정도 단일 앱 컨테이너를 기준으로 구성해도 문제가 없어 보였다.

하지만 EC2 배포 환경에서는 무중단 배포를 위해 Spring Boot 애플리케이션이 blue/green 컨테이너로 나뉘어 실행되고 있었다.

| 환경 | 앱 컨테이너 |
| --- | --- |
| 로컬 Docker | `hankkipot-app` |
| EC2 blue | `hankkipot-app-blue` |
| EC2 green | `hankkipot-app-green` |

이 차이 때문에 배포 환경에서 Grafana Explore에서 Loki 로그를 조회하려고 했을 때 `No data`, `No labels found`가 발생했다.

처음에는 Grafana 쿼리 문제라고 생각했지만, 확인 결과 Loki로 로그가 들어오지 않고 있었다.

즉, 문제의 핵심은 **모니터링/알림 설정이 무중단 배포 구조가 아니라 단일 앱 컨테이너 구조를 기준으로 되어 있었다는 점**이었다.

---

## 발생한 증상

| 증상 | 내용 |
| --- | --- |
| Grafana Explore에서 Loki 라벨 없음 | Label browser에서 `No labels found` 표시 |
| Loki 로그 조회 실패 | `{container="backend"}` 또는 `{job="spring-app"}` 조회 시 `No data` |
| Spring Boot 로그 확인 불가 | ERROR, Exception, WARN 로그를 Grafana에서 확인할 수 없음 |
| n8n 알림 처리 로그 확인 불가 | Grafana Alert → n8n → Slack 흐름의 로그 추적 불가 |
| 원인 파악 어려움 | Grafana datasource 문제처럼 보였지만 실제로는 Alloy 수집 대상 문제였음 |

Grafana와 Loki datasource 연결은 되어 있었지만, Loki에 실제 로그 스트림이 없었기 때문에 라벨이 뜨지 않았다.

---

## 기존 설정

처음 Alloy 설정은 로컬 Docker 환경의 단일 컨테이너 이름인 `hankkipot-app`만 수집하도록 되어 있었다.

```
discovery.relabel "app_logs" {
  targets = discovery.docker.containers.targets

  rule {
    source_labels = ["__meta_docker_container_name"]
    regex         = "/?hankkipot-app"
    action        = "keep"
  }

  rule {
    source_labels = ["__meta_docker_container_name"]
    target_label  = "container"
  }
}
```

이 설정은 Docker 컨테이너 중 이름이 `hankkipot-app`인 컨테이너만 Loki로 보내겠다는 의미이다.

로컬에서는 문제가 없었다.

하지만 배포 환경에서는 앱 컨테이너가 다음처럼 실행된다.

```
app-blue:
  container_name: hankkipot-app-blue
  ports:
    - "8080:8080"

app-green:
  container_name: hankkipot-app-green
  ports:
    - "8081:8080"
```

따라서 Alloy의 기존 정규식은 배포 환경의 앱 컨테이너를 잡지 못했다.

---

## 원인 분석

문제의 원인은 단순했다.

Alloy가 수집하려는 컨테이너 이름과 실제 배포 환경의 컨테이너 이름이 달랐다.

| 구분 | 기존 설정에서 기대한 이름 | 실제 배포 환경 이름 | 결과 |
| --- | --- | --- | --- |
| 로컬 앱 | `hankkipot-app` | `hankkipot-app` | 수집 가능 |
| blue 앱 | `hankkipot-app` | `hankkipot-app-blue` | 수집 실패 |
| green 앱 | `hankkipot-app` | `hankkipot-app-green` | 수집 실패 |
| n8n | 수집 대상 아님 | `hankkipot-n8n` | 수집 실패 |

결국 EC2 배포 환경에서는 Spring Boot 앱 로그가 Loki로 전달되지 않았다.

그래서 Grafana에서 Loki datasource를 선택해도 Label browser가 비어 있었고, 로그 패널을 만들어도 `No data`가 발생했다.

---

## Prometheus 설정은 왜 괜찮았나

Prometheus는 무중단 배포 구조를 어느 정도 반영하고 있었다.

```
scrape_configs:
  - job_name: "spring-boot-app"
    metrics_path: "/actuator/prometheus"
    scrape_interval: 15s
    static_configs:
      - targets:
          - "host.docker.internal:8080"
        labels:
          color: "blue"
      - targets:
          - "host.docker.internal:8081"
        labels:
          color: "green"
```

Prometheus는 blue와 green을 각각 `8080`, `8081`로 바라보고 있었다.

그리고 Application DOWN Alert도 다음처럼 구성되어 있었다.

```
max(up{job="spring-boot-app"})
```

이 쿼리는 blue/green 중 하나라도 살아 있으면 `1`이 된다.

즉, 무중단 배포 중 이전 컨테이너가 내려가더라도 새 컨테이너가 정상 동작 중이면 전체 애플리케이션은 살아 있다고 판단한다.

| 상태 | blue | green | `max(up)` 결과 | 알림 |
| --- | --- | --- | --- | --- |
| blue만 실행 | 1 | 0 | 1 | 발생 안 함 |
| green만 실행 | 0 | 1 | 1 | 발생 안 함 |
| 둘 다 실행 | 1 | 1 | 1 | 발생 안 함 |
| 둘 다 중단 | 0 | 0 | 0 | 발생 |

따라서 Prometheus의 Application DOWN 알림은 무중단 배포 구조에 맞게 동작할 수 있었다.

문제는 Prometheus가 아니라, **Loki 로그 수집을 담당하는 Alloy 설정이 단일 컨테이너 기준으로 남아 있던 것**이었다.

---

## n8n 알림 로그 문제

Grafana Alert는 n8n Webhook으로 전달되도록 구성되어 있었다.

```yaml
contactPoints:
  - orgId: 1
    name: n8n-webhook
    receivers:
      - uid: n8n-webhook
        type: webhook
        settings:
          url: ${GRAFANA_N8N_WEBHOOK_URL}
          httpMethod: POST
```

그리고 배포 compose에서는 Grafana에 Webhook URL 환경변수를 주입하고 있었다.

```
grafana:
  environment:
    GRAFANA_N8N_WEBHOOK_URL: ${GRAFANA_N8N_WEBHOOK_URL}
```

즉, Grafana Alert → n8n Webhook 흐름 자체는 구성되어 있었다.

하지만 n8n 컨테이너 로그는 Alloy 수집 대상에 포함되어 있지 않았다.

그래서 알림이 n8n으로 들어왔는지, Slack 전송 단계에서 실패했는지, Gemini 분석 단계에서 문제가 있었는지 Loki에서 확인할 수 없었다.

운영 환경에서는 알림 전송 자체도 중요하지만, 알림이 실패했을 때 그 원인을 추적할 수 있어야 한다.

따라서 n8n 로그도 Loki 수집 대상에 포함할 필요가 있었다.

---

## 해결 방법

해결 방향은 두 가지였다.

| 해결 항목 | 내용 |
| --- | --- |
| Spring Boot 로그 수집 대상 수정 | `hankkipot-app`, `hankkipot-app-blue`, `hankkipot-app-green` 모두 수집 |
| n8n 로그 수집 추가 | `hankkipot-n8n` 컨테이너 로그를 별도 job으로 수집 |

수정 후 Alloy 설정은 다음과 같다.

```
discovery.docker "containers" {
  host = "unix:///var/run/docker.sock"
}

discovery.relabel "app_logs" {
  targets = discovery.docker.containers.targets

  rule {
    source_labels = ["__meta_docker_container_name"]
    regex         = "/?hankkipot-app(-blue|-green)?"
    action        = "keep"
  }

  rule {
    source_labels = ["__meta_docker_container_name"]
    target_label  = "container"
  }
}

discovery.relabel "n8n_logs" {
  targets = discovery.docker.containers.targets

  rule {
    source_labels = ["__meta_docker_container_name"]
    regex         = "/?hankkipot-n8n"
    action        = "keep"
  }

  rule {
    source_labels = ["__meta_docker_container_name"]
    target_label  = "container"
  }
}

loki.source.docker "app" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.relabel.app_logs.output
  labels     = {job = "spring-app"}
  forward_to = [loki.write.local.receiver]
}

loki.source.docker "n8n" {
  host       = "unix:///var/run/docker.sock"
  targets    = discovery.relabel.n8n_logs.output
  labels     = {job = "n8n"}
  forward_to = [loki.write.local.receiver]
}

loki.write "local" {
  endpoint {
    url = "http://loki:3100/loki/api/v1/push"
  }
}
```

여기서 중요한 부분은 이 정규식이다.

```
regex = "/?hankkipot-app(-blue|-green)?"
```

이 정규식은 다음 컨테이너만 수집한다.

| 컨테이너 | 수집 여부 |
| --- | --- |
| `hankkipot-app` | 수집 |
| `hankkipot-app-blue` | 수집 |
| `hankkipot-app-green` | 수집 |
| `hankkipot-app-test` | 제외 |
| `hankkipot-app-worker` | 제외 |

처음에는 `hankkipot-app.*`처럼 더 넓은 정규식을 사용할 수도 있었지만, 나중에 `hankkipot-app-worker`, `hankkipot-app-batch` 같은 컨테이너가 추가되면 Spring Boot API 로그에 섞일 수 있다.

그래서 로컬 단일 컨테이너와 배포 blue/green 컨테이너만 정확히 잡는 방식으로 정리했다.

## 개선 전후 비교

| 구분 | 개선 전 | 개선 후 |
| --- | --- | --- |
| 앱 로그 수집 기준 | `hankkipot-app` 단일 컨테이너 | `hankkipot-app`, `hankkipot-app-blue`, `hankkipot-app-green` |
| 무중단 배포 반영 | 미반영 | 반영 |
| Loki Label browser | `No labels found` 발생 | `spring-app`, `n8n` 라벨 확인 가능 |
| n8n 로그 | 수집하지 않음 | `job="n8n"`으로 별도 수집 |
| Grafana Alert 분석 | Prometheus Alert는 가능하지만 로그 추적 어려움 | Alert 발생 후 Loki 로그로 원인 추적 가능 |
| 알림 실패 추적 | n8n 내부 처리 로그 확인 어려움 | Webhook, Slack, Gemini 관련 로그 확인 가능 |

---

## 최종 정리

이번 문제는 Grafana, Loki, n8n 자체의 문제가 아니라, **무중단 배포 구조를 반영하지 않은 로그 수집 설정 문제**였다.

로컬에서는 Spring Boot 앱이 `hankkipot-app` 하나로 실행되기 때문에 기존 설정이 정상처럼 보였다.

하지만 EC2 배포 환경에서는 blue/green 무중단 배포를 위해 앱 컨테이너가 `hankkipot-app-blue`, `hankkipot-app-green`으로 나뉘어 실행되었다.

Alloy 설정이 이 차이를 반영하지 못해 배포 환경의 Spring Boot 로그가 Loki로 전달되지 않았고, Grafana에서는 `No labels found`가 발생했다.

이를 해결하기 위해 Alloy의 Docker discovery relabel 규칙을 수정해 로컬 단일 컨테이너와 배포 blue/green 컨테이너를 모두 수집하도록 변경했다.

또한 n8n 컨테이너 로그도 별도 job으로 수집해 Grafana Alert → n8n → Gemini → Slack 알림 흐름까지 Loki에서 추적할 수 있도록 개선했다.

결과적으로 Prometheus는 장애 발생 여부를 감지하고, Loki는 Spring Boot/n8n/Kafka 로그를 통해 장애 원인을 추적하며, Grafana Alert와 n8n은 Slack 알림으로 운영자에게 전달하는 구조로 정리할 수 있었다.

---

</details>

---

<a id="ai-매칭-추천-테스트-데이터-부족으로-인한-추천-품질-저하"></a>

<details>
<summary><strong>AI 매칭 추천 테스트 데이터 부족으로 인한 추천 품질 저하</strong></summary>

## AI 매칭 추천 테스트 데이터 부족으로 인한 추천 품질 저하

## 문제 상황

매칭 AI 기능을 테스트하는 과정에서 초기에는 `initData`에 등록된 9개의 게시물만 사용해 추천 결과를 확인했다.

AI 매칭 기능의 목표는 사용자의 채팅 내용을 기반으로 관련 있는 게시물을 추천해주는 것이었다. 예를 들어 사용자가 특정 음식, 상황, 인원, 장소, 식사 목적 등을 언급하면 AI가 그 맥락을 파악하고 적절한 밥약 게시물을 추천하는 구조였다.

하지만 테스트 데이터가 9개뿐이다 보니, 사용자가 입력한 채팅 내용과 정확히 맞거나 관련성이 높은 게시물이 거의 없었다. 그 결과 AI가 추천을 하더라도 결과물이 어색하거나, 매번 비슷한 게시물만 추천되는 문제가 발생했다.

처음에는 AI 프롬프트나 추천 로직 자체의 문제라고 생각했다. 하지만 테스트를 반복하면서 실제 원인은 **AI가 선택할 수 있는 후보 게시물이 너무 적다는 점**과 **추천을 정확도 중심으로만 평가하고 있었다는 점**이라는 것을 알게 되었다.

---

## 발생한 문제

| 문제 | 내용 |
| --- | --- |
| 추천 후보 부족 | 게시물이 9개뿐이라 사용자 채팅과 관련 있는 후보가 부족했음 |
| 추천 결과 반복 | 선택지가 적어 비슷한 게시물이 계속 추천됨 |
| 결과 품질 저하 | 사용자의 채팅 맥락과 어긋나는 게시물이 추천됨 |
| 평가 기준 모호 | AI가 못 맞힌 것인지, 데이터가 부족한 것인지 구분하기 어려웠음 |
| fallback 부재 | 관련 게시물이 없을 때 대체 추천 기준이 부족했음 |

특히 사용자가 특정 음식이나 상황을 말했을 때, 후보 게시물 중 그 조건과 가까운 게시물이 없으면 AI가 억지로 하나를 고르는 느낌이 강했다.

예를 들어 사용자가 저녁 시간대에 챗봇을 사용하면서 “오늘 가볍게 밥 먹을 사람 있나요?”라고 입력했는데, 게시물 후보가 9개뿐이라 저녁 식사와 관련된 게시물이 없으면 추천 결과가 부자연스러웠다.

---

## 원인 분석

초기에는 추천 결과가 좋지 않은 이유를 AI 응답 품질 문제로 생각했다. 그래서 프롬프트를 수정하거나 추천 기준을 더 엄격하게 잡는 방향을 먼저 고민했다.

하지만 튜터님 피드백을 통해 문제를 다르게 보게 되었다.

튜터님은 게시물 9개로 계속 테스트하면 추천 결과가 좋게 나오기 어렵고, 게시물 수를 120개 정도로 늘려서 테스트해보는 것이 좋다고 조언해주셨다. 또한 추천 기능에서 중요한 것은 게시물을 정확하게 맞히는 것이 아니라, **사용자 채팅과 관련 있는 게시물을 추천해주는 것**이라고 피드백해주셨다.

즉, 이 기능은 정답을 찾는 검색 기능이 아니라, 사용자의 대화 맥락을 바탕으로 관련성 있는 게시물을 제안하는 추천 기능이었다.

또한 관련 게시물이 없을 때 아무 게시물이나 추천하면 사용자 입장에서 신뢰도가 떨어질 수 있었다. 그래서 관련성이 높은 게시물을 찾지 못한 경우에도, 완전히 랜덤으로 추천하는 것이 아니라 **사용자가 챗봇을 사용한 시간과 가장 가까운 게시물**을 우선 추천하는 fallback 기준이 필요하다고 판단했다.

---

## 개선 방향

문제를 해결하기 위해 세 가지 방향으로 개선했다.

| 개선 항목 | 개선 내용 |
| --- | --- |
| 게시물 데이터 확대 | 테스트 게시물을 9개에서 약 120개로 늘림 |
| 추천 기준 재정의 | 정확성보다 사용자 채팅과의 관련성을 중심으로 평가 |
| fallback 추천 로직 추가 | 관련 게시물이 없을 경우 챗봇 사용 시간과 가까운 게시물을 추천 |

이 개선을 통해 AI가 선택할 수 있는 후보군을 늘리고, 추천 결과가 완전히 비어 보이지 않도록 했다.

---

## 개선 전후 비교

| 구분 | 개선 전 | 개선 후 |
| --- | --- | --- |
| 게시물 수 | 9개 | 약 120개 |
| 추천 기준 | 정확한 게시물 매칭 | 사용자 채팅과의 관련성 |
| 관련 게시물 없을 때 | 어색한 추천 또는 반복 추천 | 챗봇 사용 시간과 가까운 게시물 fallback 추천 |
| 결과 품질 | 후보 부족으로 부자연스러움 | 더 자연스럽고 서비스 흐름에 맞는 추천 |
| 기능 관점 | 정답을 찾는 검색에 가까움 | 관련 후보를 제안하는 추천에 가까움 |

---

## fallback 추천 로직 추가

관련성 높은 게시물을 찾지 못했을 때는 사용자가 챗봇을 사용한 시간을 기준으로 가장 가까운 게시물을 추천하도록 로직을 구성했다.

예를 들어 사용자가 오후 6시에 챗봇을 사용했다면, 게시물 중 저녁 시간대와 가까운 모집 글을 우선 추천하는 방식이다.

```
사용자 챗봇 사용 시간: 18:10

후보 게시물:
1. 12:00 점심 같이 먹을 사람
2. 18:30 저녁 같이 먹을 사람
3. 21:00 야식 먹을 사람

fallback 추천 결과:
→ 18:30 저녁 같이 먹을 사람
```

이렇게 하면 사용자의 채팅 내용과 정확히 맞는 게시물이 없더라도, 현재 사용자의 상황과 가장 가까운 게시물을 추천할 수 있다.

---

## 로직 예시

추천 로직은 크게 두 단계로 구성했다.

1. 먼저 사용자 채팅 내용과 관련성이 높은 게시물을 찾는다.
2. 관련 게시물이 없거나 추천 점수가 낮으면, 챗봇 사용 시간과 가장 가까운 게시물을 추천한다.

```java
function recommendPost(userMessage, posts, chatTime) {
  const matchedPosts = posts.filter(post => {
    return isRelatedToUserMessage(userMessage, post);
  });

  if (matchedPosts.length > 0) {
    return sortByRelevance(userMessage, matchedPosts)[0];
  }

  return findClosestPostByTime(posts, chatTime);
}

function findClosestPostByTime(posts, chatTime) {
  return posts
    .map(post => ({
      ...post,
      timeDiff: Math.abs(
        new Date(post.meetingTime).getTime() - new Date(chatTime).getTime()
      ),
    }))
    .sort((a, b) => a.timeDiff - b.timeDiff)[0];
}
```

이 구조를 통해 AI 추천이 실패하거나 관련 게시물이 부족한 경우에도, 서비스 흐름상 납득 가능한 대체 추천을 제공할 수 있었다.

---

## 예시 상황

사용자가 다음과 같이 입력했다고 가정한다.

```
오늘 같이 밥 먹을 사람 있나요?
```

이때 음식 종류나 장소에 대한 명확한 조건이 없고, 게시물 중 관련성이 높은 후보가 부족할 수 있다.

기존에는 이런 경우 AI가 임의로 게시물을 추천하거나, 관련성이 낮은 게시물을 선택할 수 있었다.

개선 후에는 사용자가 챗봇을 사용한 시간이 오후 7시라면, 현재 시간과 가장 가까운 저녁 시간대 게시물을 우선 추천한다.

```
추천 게시물:
오늘 7시 30분에 학교 근처에서 저녁 같이 드실 분
```

이 추천은 사용자의 채팅 내용과 완벽하게 일치하는 것은 아니지만, “지금 밥 먹을 사람을 찾는다”는 사용자 의도와 시간적으로 가장 가깝기 때문에 충분히 의미 있는 추천이 될 수 있다.

---

## 추천 평가 기준 변경

튜터님 피드백을 반영해 추천 품질 평가 기준도 바꾸었다.

기존 기준은 다음과 같았다.

```
사용자 채팅과 정확히 일치하는 게시물을 찾아야 좋은 추천이다.
```

개선 후 기준은 다음과 같이 변경했다.

```
사용자 채팅과 관련 있는 게시물을 추천하고,
관련 게시물이 없다면 현재 사용자의 상황과 가장 가까운 게시물을 추천하면 좋은 추천이다.
```

이를 기준으로 다음 요소를 확인했다.

| 평가 요소 | 설명 |
| --- | --- |
| 채팅 관련성 | 사용자의 메시지와 게시물 내용이 관련 있는가 |
| 시간 관련성 | 사용자가 챗봇을 사용한 시간과 게시물 시간이 가까운가 |
| 상황 적합성 | 지금 사용자가 선택하기에 자연스러운 게시물인가 |
| 추천 다양성 | 같은 게시물만 반복 추천되지 않는가 |
| 사용자 경험 | 추천 결과가 비어 있거나 어색하게 느껴지지 않는가 |

---

## 최종 결과

게시물 수를 9개에서 약 120개로 늘리면서 AI가 선택할 수 있는 후보군이 충분해졌다. 또한 추천 기준을 “정확한 매칭”이 아니라 “사용자 채팅과 관련 있는 추천”으로 바꾸면서 결과를 더 현실적으로 평가할 수 있었다.

추가로 관련 게시물이 없을 경우에는 사용자가 챗봇을 사용한 시간을 기준으로 가장 가까운 게시물을 추천하도록 fallback 로직을 구성했다. 이 덕분에 추천 결과가 비어 보이거나 무작위로 보이는 문제를 줄일 수 있었다.

결과적으로 AI 매칭 추천 기능은 단순히 정답을 찾는 기능이 아니라, 사용자의 대화 맥락과 현재 상황을 바탕으로 적절한 밥약 게시물을 제안하는 기능으로 개선되었다.

---

## 정리

이번 트러블슈팅은 AI 추천 결과가 좋지 않았던 원인을 단순히 AI 성능 문제로 보지 않고, 테스트 데이터 수, 추천 평가 기준, fallback 로직 관점에서 다시 분석한 과정이었다.

초기에는 `initData`에 있는 9개의 게시물만으로 테스트했기 때문에 사용자의 다양한 채팅 의도와 맞는 후보가 부족했다. 이로 인해 추천 결과가 반복적이고 어색하게 나왔고, AI 기능 자체가 좋지 않아 보였다.

튜터님 피드백을 통해 게시물 수를 약 120개로 늘렸고, 추천 기능의 목표를 “정확히 맞는 게시물 찾기”가 아니라 “사용자 채팅과 관련 있는 게시물 추천”으로 재정의했다.

또한 관련 게시물이 없을 경우를 대비해, 사용자가 챗봇을 사용한 시간과 가장 가까운 게시물을 fallback으로 추천하는 로직을 추가했다.

이를 통해 추천 기능의 안정성을 높이고, 사용자가 어떤 메시지를 입력하더라도 서비스 흐름상 납득 가능한 추천 결과를 받을 수 있도록 개선했다.

---

</details>

---
