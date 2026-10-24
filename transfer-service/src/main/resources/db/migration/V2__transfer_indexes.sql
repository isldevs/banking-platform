CREATE INDEX idx_transfer_items_transfer_id
    ON transfer_items (transfer_id);

CREATE INDEX idx_transfer_status_history_transfer_created
    ON transfer_status_history (transfer_id, created_at);
