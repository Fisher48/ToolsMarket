CREATE TABLE failed_emails (
        id BIGSERIAL PRIMARY KEY,

        order_status VARCHAR(32) NOT NULL,
        recipient VARCHAR(255) NOT NULL,

        payload_json TEXT NOT NULL,
        error_message TEXT,

        failed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_failed_emails_status ON failed_emails(order_status);
CREATE INDEX idx_failed_emails_failed_at ON failed_emails(failed_at);