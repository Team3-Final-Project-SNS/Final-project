import { NotificationResponse } from '../api/notificationApi';

export const getNotificationContextLabel = (notification: NotificationResponse) => {
  if (!notification.relatedId) return null;

  if (notification.domain === 'CHAT') return null;

  if (notification.type === 'NO_SHOW_WARNING') return null;

  if (notification.domain === 'MATCH' || notification.domain === 'MEET') {
    return `매칭 #${notification.relatedId}`;
  }

  if (notification.domain === 'POST') {
    return `게시글 #${notification.relatedId}`;
  }

  if (notification.domain === 'DISPUTE') {
    return `이의제기 #${notification.relatedId}`;
  }

  return `${notification.domain.toLowerCase()}Id ${notification.relatedId}`;
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
