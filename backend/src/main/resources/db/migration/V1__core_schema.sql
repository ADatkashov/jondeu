-- JONDEU core schema. All money: NUMERIC(16,2) + explicit currency.

CREATE TABLE users (
  id UUID PRIMARY KEY,
  phone VARCHAR(20) NOT NULL UNIQUE,
  email VARCHAR(255) UNIQUE,
  password_hash VARCHAR(100),
  full_name VARCHAR(150) NOT NULL,
  role VARCHAR(30) NOT NULL,
  city VARCHAR(80) NOT NULL DEFAULT 'Almaty',
  language VARCHAR(5) NOT NULL DEFAULT 'ru',
  avatar_url VARCHAR(500),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  demo BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  family_id UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_family ON refresh_tokens(family_id);

CREATE TABLE otp_codes (
  phone VARCHAR(20) PRIMARY KEY,
  code_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  attempts INT NOT NULL DEFAULT 0
);

CREATE TABLE master_profiles (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  specialization VARCHAR(100) NOT NULL,
  category VARCHAR(40) NOT NULL,
  bio TEXT,
  experience_years INT NOT NULL DEFAULT 0,
  rating NUMERIC(3,2) NOT NULL DEFAULT 0,
  reviews_count INT NOT NULL DEFAULT 0,
  completed_projects INT NOT NULL DEFAULT 0,
  hourly_rate NUMERIC(16,2),
  currency VARCHAR(3) NOT NULL DEFAULT 'KZT',
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  available BOOLEAN NOT NULL DEFAULT TRUE,
  company_name VARCHAR(150),
  district VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_master_category ON master_profiles(category);

CREATE TABLE projects (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES users(id),
  contractor_id UUID REFERENCES users(id),
  name VARCHAR(200) NOT NULL,
  property_type VARCHAR(30) NOT NULL,
  rooms INT,
  area_m2 NUMERIC(8,2) NOT NULL,
  address VARCHAR(300),
  city VARCHAR(80) NOT NULL DEFAULT 'Almaty',
  description TEXT,
  category VARCHAR(40),
  status VARCHAR(30) NOT NULL,
  budget_min NUMERIC(16,2),
  budget_max NUMERIC(16,2),
  approved_budget NUMERIC(16,2) NOT NULL DEFAULT 0,
  currency VARCHAR(3) NOT NULL DEFAULT 'KZT',
  materials_included BOOLEAN NOT NULL DEFAULT TRUE,
  urgency VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  start_date DATE,
  end_date DATE,
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  demo BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_projects_owner ON projects(owner_id);
CREATE INDEX idx_projects_contractor ON projects(contractor_id);
CREATE INDEX idx_projects_status ON projects(status);

CREATE TABLE project_members (
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  member_role VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (project_id, user_id)
);
CREATE INDEX idx_members_user ON project_members(user_id);

-- Sync log + activity feed. seq gives clients a monotonic cursor for catch-up after reconnect.
CREATE TABLE project_events (
  seq BIGSERIAL PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  type VARCHAR(60) NOT NULL,
  entity_type VARCHAR(40) NOT NULL,
  entity_id UUID,
  actor_id UUID REFERENCES users(id),
  message VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_project_seq ON project_events(project_id, seq);

CREATE TABLE proposals (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  master_id UUID NOT NULL REFERENCES users(id),
  price NUMERIC(16,2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'KZT',
  duration_days INT NOT NULL,
  message TEXT,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, master_id)
);

CREATE TABLE estimates (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  number VARCHAR(40) NOT NULL,
  version INT NOT NULL DEFAULT 1,
  contractor_id UUID NOT NULL REFERENCES users(id),
  status VARCHAR(20) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'KZT',
  additional_costs NUMERIC(16,2) NOT NULL DEFAULT 0,
  discount NUMERIC(16,2) NOT NULL DEFAULT 0,
  tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
  subtotal NUMERIC(16,2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(16,2) NOT NULL DEFAULT 0,
  total NUMERIC(16,2) NOT NULL DEFAULT 0,
  review_comment VARCHAR(500),
  approved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, number, version)
);
CREATE INDEX idx_estimates_project ON estimates(project_id);

CREATE TABLE estimate_items (
  id UUID PRIMARY KEY,
  estimate_id UUID NOT NULL REFERENCES estimates(id) ON DELETE CASCADE,
  position INT NOT NULL,
  kind VARCHAR(10) NOT NULL,
  title VARCHAR(200) NOT NULL,
  description VARCHAR(500),
  quantity NUMERIC(14,3) NOT NULL,
  unit VARCHAR(20) NOT NULL,
  unit_price NUMERIC(16,2) NOT NULL,
  total NUMERIC(16,2) NOT NULL
);
CREATE INDEX idx_items_estimate ON estimate_items(estimate_id);

CREATE TABLE stages (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  position INT NOT NULL,
  code VARCHAR(40),
  title VARCHAR(200) NOT NULL,
  description VARCHAR(500),
  weight NUMERIC(6,2) NOT NULL DEFAULT 1,
  budget NUMERIC(16,2) NOT NULL DEFAULT 0,
  start_date DATE,
  end_date DATE,
  actual_end_date DATE,
  status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
  progress INT NOT NULL DEFAULT 0,
  depends_on UUID REFERENCES stages(id),
  assignee_id UUID REFERENCES users(id),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_stages_project ON stages(project_id, position);

CREATE TABLE stage_comments (
  id UUID PRIMARY KEY,
  stage_id UUID NOT NULL REFERENCES stages(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES users(id),
  body VARCHAR(1000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE materials (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name VARCHAR(200) NOT NULL,
  unit VARCHAR(20) NOT NULL,
  planned_qty NUMERIC(14,3) NOT NULL,
  unit_price NUMERIC(16,2) NOT NULL,
  actual_unit_price NUMERIC(16,2),
  received_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
  supplier VARCHAR(150),
  status VARCHAR(25) NOT NULL DEFAULT 'PLANNED',
  delivery_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_materials_project ON materials(project_id);

CREATE TABLE payments (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  stage_id UUID REFERENCES stages(id),
  payer_id UUID NOT NULL REFERENCES users(id),
  type VARCHAR(20) NOT NULL,
  method VARCHAR(20) NOT NULL,
  amount NUMERIC(16,2) NOT NULL CHECK (amount > 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'KZT',
  status VARCHAR(20) NOT NULL,
  provider VARCHAR(30) NOT NULL,
  provider_ref VARCHAR(100),
  reference VARCHAR(40) NOT NULL UNIQUE,
  idempotency_key VARCHAR(100) NOT NULL UNIQUE,
  requested_by UUID REFERENCES users(id),
  note VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_at TIMESTAMPTZ
);
CREATE INDEX idx_payments_project ON payments(project_id);

CREATE TABLE ledger_entries (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  payment_id UUID REFERENCES payments(id),
  kind VARCHAR(20) NOT NULL,
  amount NUMERIC(16,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  actor_id UUID REFERENCES users(id),
  note VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_project ON ledger_entries(project_id);

CREATE TABLE conversations (
  id UUID PRIMARY KEY,
  project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
  title VARCHAR(200),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE conversation_members (
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  last_read_at TIMESTAMPTZ NOT NULL DEFAULT 'epoch',
  PRIMARY KEY (conversation_id, user_id)
);
CREATE TABLE messages (
  id UUID PRIMARY KEY,
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users(id),
  body TEXT,
  media_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conv ON messages(conversation_id, created_at);

CREATE TABLE media (
  id UUID PRIMARY KEY,
  project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
  stage_id UUID REFERENCES stages(id) ON DELETE SET NULL,
  uploader_id UUID NOT NULL REFERENCES users(id),
  category VARCHAR(20) NOT NULL,
  filename VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL,
  storage_key VARCHAR(300) NOT NULL,
  caption VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_media_project ON media(project_id);

CREATE TABLE inspections (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  stage_id UUID REFERENCES stages(id),
  title VARCHAR(200) NOT NULL,
  inspection_date DATE NOT NULL,
  inspector_id UUID REFERENCES users(id),
  result VARCHAR(25) NOT NULL DEFAULT 'PENDING',
  comments VARCHAR(1000),
  corrective_actions VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inspections_project ON inspections(project_id);

CREATE TABLE calendar_events (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  type VARCHAR(25) NOT NULL,
  title VARCHAR(200) NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ,
  created_by UUID REFERENCES users(id)
);
CREATE INDEX idx_calendar_project ON calendar_events(project_id, starts_at);

CREATE TABLE reviews (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  author_id UUID NOT NULL REFERENCES users(id),
  master_id UUID NOT NULL REFERENCES users(id),
  rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  body VARCHAR(2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (project_id, author_id, master_id)
);

CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  project_id UUID,
  type VARCHAR(60) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body VARCHAR(500),
  read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);

CREATE TABLE audit_log (
  id BIGSERIAL PRIMARY KEY,
  actor_id UUID,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(40),
  entity_id UUID,
  detail VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
