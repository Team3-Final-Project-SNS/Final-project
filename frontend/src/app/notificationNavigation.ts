import { NotificationResponse } from '../api/notificationApi';

export const getNotificationContextLabel = (notification: NotificationResponse) => {
  if (!notification.relatedId) return null;

  if (notification.domain === 'CHAT') return null;

  if (notification.domain === 'MATCH' || notification.domain === 'MEET') {
    return `매칭 #${notification.relatedId}`;
  }

  return `${notification.domain.toLowerCase()}Id ${notification.relatedId}`;
};

export const getNotificationTargetPath = (notification: NotificationResponse) => {
  const { type, domain, relatedId } = notification;
  const title = notification.title || '';
  const content = notification.content || '';
  const isPostDeleteNotification =
    type === 'POST_DELETED' ||
    (type === 'SYSTEM' &&
      Boolean(relatedId) &&
      (title.includes('삭제') || content.includes('삭제')) &&
      (content.includes('게시글') || content.includes('게시물') || content.includes('게시')));

  if (type === 'CHAT_MEMBER_LEFT') {
    return null;
  }

  if (relatedId) {
    if (isPostDeleteNotification) {
      return `/posts/${relatedId}/delete-reason`;
    }

    switch (type) {
      case 'DISPUTE_SUBMITTED':
        return `/admin/disputes?disputeId=${relatedId}`;
      case 'REPORT_SUBMITTED':
        return `/admin/reports?reportId=${relatedId}`;
      case 'INQUIRY_SUBMITTED':
        return `/admin/inquiries?inquiryId=${relatedId}`;
      case 'PLACE_VERIFIED':
        return `/matches/${relatedId}/place-verification`;
      case 'MEET_COMPLETED':
      case 'REVIEW_DEADLINE_REMINDER':
        return `/matches?filter=COMPLETED&reviewMatchId=${relatedId}`;
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
      case 'REPORT_REWARD':
      case 'REPORT_REJECTED':
        return `/me/reports?reportId=${relatedId}`;
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
    case 'REPORT':
      return '/me/reports';
    case 'DISPUTE':
      return '/me/matches';
    case 'INQUIRY':
      return '/me/support/inquiries';
    case 'ACCOUNT':
    case 'SYSTEM':
    default:
      return '/me';
  }
};
