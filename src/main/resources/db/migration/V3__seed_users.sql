-- Dev-only seed users. Passwords are bcrypt hashes generated offline (never store plaintext).
-- admin / admin123 (ADMIN), analyst / analyst123 (ANALYST)
INSERT INTO users (username, password_hash, role) VALUES
    ('admin', '$2a$10$aUpujqCmq3aKxKjT6Rr2G.rT/nyNY/H70B5VTdk.HU0BOICGmN/ZG', 'ADMIN'),
    ('analyst', '$2a$10$q2W0tEVtMoHmvUh0eVsKnu8ppr2SFbN2KjIwcaMBiynzyaZRi9oEu', 'ANALYST');
