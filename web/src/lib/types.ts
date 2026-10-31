export type Role = 'CUSTOMER' | 'MASTER' | 'CONTRACTOR' | 'COMPANY_ADMIN' | 'PROJECT_MANAGER' | 'INSPECTOR' | 'PLATFORM_ADMIN' | 'PLATFORM_SUPER_ADMIN';
export type ProjectRole = 'OWNER' | 'CONTRACTOR' | 'PROJECT_MANAGER' | 'MASTER' | 'INSPECTOR' | 'ADMIN';
export type ProjectStatus = 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'PROPOSALS_RECEIVED' | 'CONTRACTOR_SELECTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export type StageStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'UNDER_REVIEW' | 'COMPLETED' | 'BLOCKED' | 'DELAYED';
export type EstimateStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED' | 'SUPERSEDED';
export type PaymentStatus = 'REQUESTED' | 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED' | 'REFUNDED';
export type PaymentMethod = 'CASH' | 'CARD' | 'KASPI' | 'BANK_TRANSFER';
export type PaymentType = 'ADVANCE' | 'STAGE' | 'MATERIAL' | 'FINAL' | 'ADDITIONAL';
export type MaterialStatus = 'PLANNED' | 'ORDERED' | 'PARTIALLY_DELIVERED' | 'DELIVERED' | 'CANCELLED';
export type InspectionResult = 'PASSED' | 'NEEDS_IMPROVEMENT' | 'FAILED' | 'PENDING';

export interface User { id: string; phone: string; email?: string; fullName: string; role: Role; city: string; language: string; avatarUrl?: string }
export interface Tokens { accessToken: string; refreshToken: string; expiresInSeconds: number; user: User }
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }

export interface Project {
  id: string; ownerId: string; ownerName?: string; contractorId?: string; contractorName?: string; name: string; propertyType: string; rooms?: number; areaM2: number;
  address?: string; city: string; description?: string; category?: string; status: ProjectStatus; budgetMin?: number; budgetMax?: number; approvedBudget: number; currency: string;
  materialsIncluded: boolean; urgency: string; startDate?: string; endDate?: string; archived: boolean; demo: boolean; myRole?: ProjectRole; createdAt: string; updatedAt: string;
}
export interface Stage {
  id: string; projectId: string; position: number; code?: string; title: string; description?: string; weight: number; budget: number; startDate?: string; endDate?: string;
  actualEndDate?: string; status: StageStatus; progress: number; dependsOn?: string; assigneeId?: string; version?: number;
}
export interface Overview {
  project: Project;
  finance: { currency: string; approvedBudget: number; paid: number; reserved: number; remaining: number; utilizationPercent: number; materialsPlanned: number; materialsActual: number; costBreakdown: Record<string, number>; pendingRequests: number };
  progress: { percent: number; stagesTotal: number; stagesCompleted: number; currentStage?: Stage };
  schedule: { startDate?: string; endDate?: string; daysLeft?: number; overdueStages: number };
  quality: { status: InspectionResult; inspections: number; byResult: Record<string, number> };
  counts: { photos: number; pendingEstimates: number; openPaymentRequests: number };
  lastSeq: number;
}
export interface EstimateItem { id: string; position: number; kind: 'LABOR' | 'MATERIAL' | 'OTHER'; title: string; description?: string; quantity: number; unit: string; unitPrice: number; total: number }
export interface Estimate {
  id: string; projectId: string; number: string; version: number; contractorId: string; status: EstimateStatus; currency: string; subtotal: number; additionalCosts: number; discount: number;
  taxRate: number; taxAmount: number; total: number; paid: number; remaining: number; reviewComment?: string; approvedAt?: string; createdAt: string; updatedAt: string; items: EstimateItem[];
}
export interface Payment {
  id: string; projectId: string; stageId?: string; payerId: string; requestedBy?: string; type: PaymentType; method: PaymentMethod; amount: number; currency: string; status: PaymentStatus;
  provider: string; providerRef?: string; reference: string; note?: string; createdAt: string; paidAt?: string; sandbox: boolean;
}
export interface Material {
  id: string; projectId: string; name: string; unit: string; plannedQty: number; unitPrice: number; plannedCost: number; actualUnitPrice?: number; actualCost: number; receivedQty: number;
  supplier?: string; status: MaterialStatus; deliveryDate?: string;
}
export interface Inspection { id: string; projectId: string; stageId?: string; title: string; inspectionDate: string; inspectorId?: string; result: InspectionResult; comments?: string; correctiveActions?: string }
export interface CalendarItem { id: string; type: string; title: string; date: string; startsAt?: string; endsAt?: string; source: string; refId: string; editable: boolean; status?: string }
export interface MediaItem { id: string; projectId?: string; stageId?: string; uploaderId: string; category: string; filename: string; contentType: string; sizeBytes: number; caption?: string; createdAt: string; url: string }
export interface ProjectEvent { kind: 'project.event'; seq: number; projectId: string; type: string; entityType: string; entityId?: string; actorId?: string; message?: string; createdAt: string }
export interface Master {
  id: string; fullName: string; avatarUrl?: string; city: string; district?: string; specialization: string; category: string; bio?: string; experienceYears: number; rating: number;
  reviewsCount: number; completedProjects: number; hourlyRate?: number; currency: string; verified: boolean; available: boolean; companyName?: string;
}
export interface Member { userId: string; fullName: string; role: ProjectRole }
export interface Proposal { id: string; projectId: string; masterId: string; masterName?: string; price: number; currency: string; durationDays: number; message?: string; status: 'PENDING' | 'ACCEPTED' | 'REJECTED'; createdAt: string }
export interface OpenRequest { id: string; name: string; category?: string; propertyType: string; areaM2: number; city: string; budgetMin?: number; budgetMax?: number; description?: string; status: ProjectStatus; createdAt: string; proposalsCount: number; myProposal?: string }
export interface Conversation { id: string; projectId?: string; title: string; memberIds: string[]; lastMessage?: ChatMessage; unread: number }
export interface ChatMessage { id: string; conversationId: string; senderId: string; senderName?: string; body?: string; attachment?: { id: string; filename: string; contentType: string; sizeBytes: number; url: string }; createdAt: string; status: 'SENT' | 'READ' }
export interface AppNotification { id: string; projectId?: string; type: string; title: string; body?: string; read: boolean; createdAt: string }
export interface Category { code: string; ru: string; kk: string; en: string }
