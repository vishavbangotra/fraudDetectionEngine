export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export type UiStatus = 'approved' | 'review' | 'blocked';

export interface Transaction {
  transactionId: string;
  customerId: string;
  merchantId: string;
  amount: number;
  country: string;
  city?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  deviceId: string;
  ipAddress?: string | null;
  timestamp: string;
}

export interface ScoredTransaction {
  transaction: Transaction;
  score: number;
  triggeredRules: string[];
  riskLevel: RiskLevel;
}

export function statusFromRisk(level: RiskLevel): UiStatus {
  switch (level) {
    case 'HIGH':
      return 'blocked';
    case 'MEDIUM':
      return 'review';
    default:
      return 'approved';
  }
}
