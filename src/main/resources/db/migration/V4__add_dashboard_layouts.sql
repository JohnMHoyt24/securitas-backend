CREATE TABLE dashboard_layouts (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    layout_json JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
