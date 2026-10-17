ALTER TABLE alerts ADD COLUMN fingerprint VARCHAR(128) NOT NULL;
CREATE UNIQUE INDEX idx_alerts_fingerprint ON alerts(fingerprint);
