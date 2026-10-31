'use client';
import { Badge, type Tone } from '@/components/ui';
import { useT } from '@/lib/i18n';

const TONES: Record<string, Tone> = {
  // project / request
  DRAFT: 'neutral', SUBMITTED: 'blue', UNDER_REVIEW: 'yellow', PROPOSALS_RECEIVED: 'orange', CONTRACTOR_SELECTED: 'blue', IN_PROGRESS: 'blue', COMPLETED: 'green', CANCELLED: 'red',
  // stages
  NOT_STARTED: 'neutral', BLOCKED: 'red', DELAYED: 'orange',
  // estimates
  APPROVED: 'green', REJECTED: 'red', CHANGES_REQUESTED: 'yellow', SUPERSEDED: 'neutral',
  // payments
  REQUESTED: 'yellow', PENDING: 'yellow', PAID: 'green', FAILED: 'red', REFUNDED: 'neutral',
  // materials
  PLANNED: 'neutral', ORDERED: 'blue', PARTIALLY_DELIVERED: 'orange', DELIVERED: 'green',
  // inspections
  PASSED: 'green', NEEDS_IMPROVEMENT: 'orange',
  // proposals
  ACCEPTED: 'green',
};

export function StatusBadge({ status }: { status: string }) {
  const t = useT();
  return <Badge tone={TONES[status] ?? 'neutral'}>{t(`status.${status}`)}</Badge>;
}
