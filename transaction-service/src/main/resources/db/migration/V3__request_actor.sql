-- Existing requests deliberately retain NULL: their initiating identity cannot be inferred safely.
ALTER TABLE transactions ADD COLUMN request_subject VARCHAR(255);
