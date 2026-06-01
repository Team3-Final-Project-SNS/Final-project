package com.example.team3final.domain.ai.support.tool;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 고객센터 AI가 답변 근거를 얻기 위해 호출하는 Spring AI Tool입니다.
 *
 * 기능/정책 안내는 카테고리별 가이드로 제공하고,
 * 개인 상태가 필요한 질문은 로그인 사용자의 포인트와 계정 상태를 조회합니다.
 */
@Component
@RequiredArgsConstructor
public class AiSupportTool {

    private final UserService userService;


    /**
     * 카테고리별 고객센터 안내 문서를 조회합니다.
     *
     * LLM은 사용자의 자연어 질문을 MATCH, POST, POINT, CHAT, REPORT,
     * ACCOUNT, MEET, GENERAL 중 하나로 분류한 뒤 이 Tool을 호출합니다.
     * 현재는 switch 기반 기본 안내를 반환하지만, 추후 RAG 문서 검색으로 교체해도
     * 서비스 계층의 Tool 호출 흐름은 유지할 수 있습니다.
     *
     * @ToolParam은 LLM이 Tool을 호출할 때 어떤 인자를 넣어야 하는지 알려주는 설명서입니다.
     * 여기서는 category가 필수이며, 허용 enum 값을 description에 적어 모델의 잘못된 호출을 줄입니다.
     */
    @Tool(
            description = "한끼팟 기능, 정책, 사용 방법을 카테고리별로 조회합니다.",
            resultConverter = AiSupportToolResultConverter.class
    )
    public AiSupportGuideToolResult getServiceGuide(
            @ToolParam(description = "문의 카테고리. MATCH, POST, POINT, CHAT, REPORT, ACCOUNT, MEET, GENERAL 중 하나", required = true)
            AiSupportCategory category
    ) {
        return switch (category) {
            case MATCH -> new AiSupportGuideToolResult(
                    AiSupportCategory.MATCH,
                    "매칭 신청 및 취소 안내",
                    """
                    모집 중인 게시글에 신청하면 매칭이 생성됩니다.
                    본인이 작성한 게시글에는 신청할 수 없습니다.
                    이미 신청한 게시글에는 중복 신청할 수 없습니다.
                    모집이 종료되었거나 인원이 마감된 게시글에는 신청할 수 없습니다.
                    약속 시간이 지난 뒤에는 매칭 취소가 제한됩니다.
                    매칭 취소 시 시점과 정책에 따라 예치 포인트가 전액 또는 일부 반환될 수 있습니다.
                    """,
                    "/api/v1/matches",
                    true
            );
            case POST -> new AiSupportGuideToolResult(
                    AiSupportCategory.POST,
                    "게시글 작성 및 관리 안내",
                    """
                    게시글 작성 시 만남 시간, 장소, 한마디, 책임비, 모집 인원을 입력합니다.
                    만남 시간은 현재 이후여야 합니다.
                    책임비는 최소 200P 이상이며 100P 단위로 설정합니다.
                    게시글 작성자는 책임비를 예치합니다.
                    OPEN 상태의 본인 게시글만 수정 또는 삭제할 수 있습니다.
                    매칭이 확정되었거나 완료된 게시글은 일반 수정/삭제가 제한됩니다.
                    """,
                    "/api/v1/posts",
                    true
            );
            case POINT -> new AiSupportGuideToolResult(
                    AiSupportCategory.POINT,
                    "포인트와 정산 안내",
                    """
                    회원가입 시 가입 보너스 포인트가 지급됩니다.
                    포인트는 책임비 예치, 충전, 환불, 패널티, 신고 포상 등으로 변동됩니다.
                    충전 패키지는 3,000P, 5,000P, 10,000P, 20,000P 단위입니다.
                    정상 완료 또는 취소 정책에 따라 예치 포인트가 반환될 수 있습니다.
                    노쇼 등 패널티 상황에서는 포인트가 차감될 수 있습니다.
                    포인트 변동 내역은 포인트 거래내역에서 확인할 수 있습니다.
                    """,
                    "/api/v1/point-transactions",
                    true
            );
            case CHAT -> new AiSupportGuideToolResult(
                    AiSupportCategory.CHAT,
                    "채팅 및 알림 안내",
                    """
                    매칭이 생성되면 매칭 참여자 간 채팅방을 사용할 수 있습니다.
                    채팅 메시지는 부적절한 표현 필터링 대상이 될 수 있습니다.
                    신고 처리 결과, 포인트 지급, 매칭 관련 주요 이벤트는 알림으로 안내됩니다.
                    채팅방이 비활성화된 경우 메시지 전송이 제한될 수 있습니다.
                    """,
                    "/api/v1/chat",
                    true
            );
            case REPORT -> new AiSupportGuideToolResult(
                    AiSupportCategory.REPORT,
                    "신고 접수 및 처리 안내",
                    """
                    게시글에 문제가 있으면 신고를 접수할 수 있습니다.
                    본인 게시글은 신고할 수 없습니다.
                    같은 대상에 대한 중복 신고는 제한됩니다.
                    신고 사유는 스팸, 음란, 사기, 욕설/비방, 기타로 구분됩니다.
                    신고가 채택되면 신고자에게 50P 포상이 지급될 수 있습니다.
                    신고가 기각된 경우 일정 기간 동일 대상 재신고가 제한될 수 있습니다.
                    """,
                    "/api/v1/reports",
                    true
            );
            case ACCOUNT -> new AiSupportGuideToolResult(
                    AiSupportCategory.ACCOUNT,
                    "회원가입, 로그인, 계정 안내",
                    """
                    학교 이메일 인증을 통해 가입합니다.
                    등록된 학교 도메인이 아니면 가입이 제한될 수 있습니다.
                    닉네임은 중복 사용할 수 없습니다.
                    계정이 정지 또는 탈퇴 상태이면 서비스 이용이 제한될 수 있습니다.
                    비밀번호와 닉네임, 학과 정보는 내 정보 수정에서 변경할 수 있습니다.
                    """,
                    "/api/v1/auth, /api/v1/users",
                    true
            );
            case MEET -> new AiSupportGuideToolResult(
                    AiSupportCategory.MEET,
                    "만남 인증과 노쇼 안내",
                    """
                    매칭 후 약속 장소에서 GPS 장소 인증을 진행합니다.
                    장소 인증은 약속 장소 반경 기준과 인증 가능 시간 조건을 만족해야 합니다.
                    이후 QR 인증으로 실제 만남 완료 여부를 확인할 수 있습니다.
                    인증을 완료하지 않으면 노쇼 후보로 판정될 수 있습니다.
                    필요한 경우 만남 시간 연장을 요청할 수 있으며, 상대방 수락이 필요합니다.
                    """,
                    "/api/v1/meets",
                    true
            );
            case GENERAL -> new AiSupportGuideToolResult(
                    AiSupportCategory.GENERAL,
                    "고객센터 이용 안내",
                    """
                    AI 고객센터는 한끼팟 기능 사용법과 기본 정책을 안내합니다.
                    결제 오류, 계정 제재 이의, 예외적인 환불 요청처럼 개인 확인이 필요한 문제는 1:1 문의로 접수해야 합니다.
                    문의는 하루 접수 제한과 짧은 쿨다운이 있을 수 있습니다.
                    이미 처리 중인 같은 카테고리 문의가 있으면 중복 접수가 제한될 수 있습니다.
                    """,
                    "/api/v1/inquiries",
                    true
            );
        };
    }

    /**
     * 로그인 사용자의 개인 상태를 조회합니다.
     *
     * 보유 포인트, 계정 상태, 닉네임처럼 개인 맞춤 안내에 필요한 정보가 있을 때
     * LLM이 이 Tool을 호출합니다. 비밀번호, 토큰, 민감 인증 정보는 반환하지 않고,
     * 답변에 필요한 최소 사용자 컨텍스트만 제공합니다.
     */
    public AiSupportUserContextToolResult getUserSupportContext(String email) {
        User user = userService.findByEmail(email);
        UserInfoDto userInfo = userService.getUserInfo(user.getId());

        return new AiSupportUserContextToolResult(
                userInfo.userId(),
                userInfo.nickname(),
                userInfo.major(),
                userInfo.studentNumber(),
                user.getTotalPoint(),
                user.getStatus().name()
        );
    }
}
