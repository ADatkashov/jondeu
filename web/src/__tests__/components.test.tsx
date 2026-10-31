import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '@/lib/i18n';
import { StatusBadge } from '@/components/Status';
import { Button, EmptyState, Field, Input, Progress } from '@/components/ui';

const wrap = (ui: React.ReactNode) => render(<I18nProvider>{ui}</I18nProvider>);

describe('ui components', () => {
  it('renders localized status badges', () => { wrap(<StatusBadge status="IN_PROGRESS" />); expect(screen.getByText('В работе')).toBeInTheDocument(); });
  it('Field shows translated validation errors accessibly', () => {
    wrap(<Field label="Телефон" error="val.phone"><Input /></Field>);
    expect(screen.getByRole('alert')).toHaveTextContent('Введите номер Казахстана');
  });
  it('Progress clamps its value and exposes ARIA', () => {
    wrap(<Progress value={140} />);
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
  });
  it('Button is disabled and not clickable while loading', async () => {
    const onClick = vi.fn();
    wrap(<Button loading onClick={onClick}>Pay</Button>);
    await userEvent.click(screen.getByRole('button'));
    expect(onClick).not.toHaveBeenCalled();
  });
  it('EmptyState shows title and hint', () => { wrap(<EmptyState title="Пусто" text="Подсказка" />); expect(screen.getByText('Подсказка')).toBeInTheDocument(); });
});
