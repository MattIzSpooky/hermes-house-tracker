CREATE TABLE agent_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    client_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    schedule VARCHAR(100),
    last_run_at TIMESTAMP WITH TIME ZONE,
    next_run_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES agent_tasks(id) ON DELETE CASCADE,
    client_id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    listing_ids JSONB DEFAULT '[]',
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    email_sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_agent_tasks_status_next_run ON agent_tasks(status, next_run_at);
CREATE INDEX idx_notifications_client_id_created ON notifications(client_id, created_at DESC);
