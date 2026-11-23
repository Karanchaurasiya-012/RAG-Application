-- Seed default application roles. Fixed UUIDs keep local/test environments deterministic.

INSERT INTO roles (id, name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'USER'),
    ('22222222-2222-2222-2222-222222222222', 'ADMIN');
