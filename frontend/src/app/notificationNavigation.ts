import { NotificationResponse } from '../api/notificationApi';

const relatedDomainLabels: Record<string, string> = {
  POST: '게시글',
  MATCH: '매칭',
  MEET: '매칭',
  CHAT: '채팅방',
  REVIEW: '리뷰',
  REPORT: '신고',
  DISPUTE: '이의제기',
  INQUIRY: '문의',
  PAYMENT: '결제',
  POINT: '포인트',
  USER: '사용자',
  ACCOUNT: '계정',
  NOTIFICATION: '알림',
  SYSTEM: '시스템',
};

const idTokenLabels: Record<string, string> = {
  postId: '게시글',
  matchId: '매칭',
  meetId: '매칭',
  chatRoomId: '채팅방',
  reviewId: '리뷰',
  reportId: '신고',
  disputeId: '이의제기',
  inquiryId: '문의',
  paymentId: '결제',
  pointTransactionId: '포인트 거래',
  userId: '사용자',
  notificationId: '알림',
};

export const formatNotificationText = (value: string) => {
  return Object.entries(idTokenLabels).reduce((text, [token, label]) => {
    const pattern = new RegExp(`\\b${token}\\b\\s*#?\\s*(\\d+)`, 'gi');
    return text.replace(pattern, `${label} #$1`);
  }, value);
};

export const getNotificationContextLabel = (notification: NotificationResponse) => {
  if (!notification.relatedId) return null;

  if (notification.domain === 'CHAT') return null;

  const label = relatedDomainLabels[notification.domain] || notification.domain;
  return `${label} #${notification.relatedId}`;
};

export const getNotificationTargetPath = (notification: NotificationResponse) => {
  const { type, domain, relatedId } = notification;

  if (domain === 'REPORT' || type === 'REPORT_SUBMITTED' || type === 'REPORT_REWARD' || type === 'REPORT_REJECTED') {
    return null;
  }

  if (type === 'CHAT_MEMBER_LEFT') {
    return null;
  }

  if (type === 'POST_DELETED') {
    return notification.content.includes('환불') ? '/me/points' : relatedId ? `/posts/${relatedId}/delete-reason` : null;
  }

  if (domain === 'DISPUTE' || type === 'DISPUTE_PENDING' || type === 'DISPUTE_RESULT' || type === 'DISPUTE_DEADLINE_REMINDER') {
    return relatedId ? `/me/support/disputes/no-show?disputeId=${relatedId}` : '/me/support/disputes/no-show';
  }

  if (relatedId) {
    switch (type) {
      case 'DISPUTE_SUBMITTED':
        return `/admin/disputes?disputeId=${relatedId}`;
      case 'INQUIRY_SUBMITTED':
        return `/admin/inquiries?inquiryId=${relatedId}`;
      case 'PLACE_VERIFIED':
        return `/matches/${relatedId}/place-verification`;
      case 'MEET_COMPLETED':
      case 'REVIEW_DEADLINE_REMINDER':
        return `/matches?filter=COMPLETED&reviewMatchId=${relatedId}`;
      case 'MEET_COMPLETED_AUTHOR':
        // 등록자는 후기 작성 대상이 아니므로 매칭 상세로 이동
        return `/matches/${relatedId}`;
      case 'NO_SHOW_WARNING':
      case 'OPPONENT_NO_SHOW_WARNING':
      case 'NO_SHOW_CONFIRMED':
        return `/matches/${relatedId}`;
      case 'PAYMENT_SUCCESS':
      case 'PAYMENT_FAILED':
      case 'PAYMENT_CANCEL_SUCCESS':
      case 'PAYMENT_CANCEL_FAILED':
        return `/payments?paymentId=${relatedId}`;
      case 'INQUIRY_ANSWERED':
        return `/me/support/inquiries?inquiryId=${relatedId}`;
    }
  }

  switch (domain) {
    case 'POST':
      return relatedId ? `/posts/${relatedId}` : '/posts';
    case 'MATCH':
    case 'MEET':
      return relatedId ? `/matches/${relatedId}` : '/matches';
    case 'CHAT':
      return relatedId ? `/chat/${relatedId}` : '/matches';
    case 'POINT':
      return '/me/points';
    case 'INQUIRY':
      return '/me/support/inquiries';
    case 'ACCOUNT':
    case 'SYSTEM':
    default:
      return null;
  }
};
