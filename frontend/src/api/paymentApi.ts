import axiosInstance from './axiosInstance';
import { ApiResponse } from './authApi';
import { PageResponse } from './postApi';

export type PaymentStatus = 'READY' | 'PAID' | 'CANCELLED' | 'FAILED';
export type PayMethod = 'CARD' | 'KAKAOPAY' | 'TOSSPAY' | 'NAVERPAY';

export interface CreatePaymentResponse {
  paymentId: number;
  merchantUid: string;
  chargePoint: number;
  amount: number;
  status: PaymentStatus;
  createdAt: string;
}

export interface VerifyPaymentResponse {
  paymentId: number;
  impUid: string;
  chargePoint: number;
  amount: number;
  status: PaymentStatus;
  balanceAfter: number;
  completedAt: string;
}

export interface GetPaymentResponse {
  paymentId: number;
  chargePoint: number;
  amount: number;
  payMethod: string;
  status: PaymentStatus;
  createdAt: string;
  completedAt: string | null;
}

export interface CancelPaymentResponse {
  paymentId: number;
  status: PaymentStatus;
  refundedAmount: number;
  cancelledAt: string;
}

export const createPayment = (chargePoint: number, payMethod: string) =>
  axiosInstance.post<ApiResponse<CreatePaymentResponse>>('/api/v1/payments', {
    chargePoint,
    payMethod,
  });

export const verifyPayment = (paymentId: number, impUid: string) =>
  axiosInstance.post<ApiResponse<VerifyPaymentResponse>>(`/api/v1/payments/${paymentId}/verify`, {
    impUid,
  });

export const getMyPayments = (page: number = 0, size: number = 20) =>
  axiosInstance.get<ApiResponse<PageResponse<GetPaymentResponse>>>('/api/v1/payments/me', {
    params: { page, size },
  });

export const cancelPayment = (paymentId: number) =>
  axiosInstance.patch<ApiResponse<CancelPaymentResponse>>(`/api/v1/payments/${paymentId}/cancel`);

// PortOne 결제창에서 실패하거나 사용자가 취소한 결제 건을 FAILED 상태로 변경합니다.
export const failPayment = (paymentId: number) =>
  axiosInstance.patch<ApiResponse<null>>(`/api/v1/payments/${paymentId}/fail`);
