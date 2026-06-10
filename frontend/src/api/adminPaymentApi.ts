import axiosInstance from './axiosInstance';
import { ApiResponse } from './authApi';
import { PageResponse } from './postApi';
import { PaymentStatus } from './paymentApi';

export interface AdminPaymentItem {
  paymentId: number;
  userId: number;
  merchantUid: string;
  chargePackage: string;
  chargePoint: number;
  amount: number;
  payMethod: string | null;
  status: PaymentStatus;
  cancelReason: string | null;
  failReason: string | null;
  createdAt: string;
  completedAt: string | null;
  cancelledAt: string | null;
}

export const getAdminPayments = (
  userId?: number,
  status?: PaymentStatus,
  page: number = 0,
  size: number = 50,
) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminPaymentItem>>>('/api/v1/admin/payments', {
    params: { userId, status, page, size },
  });
