-- One row per file processed (an upload or a folder scan).
CREATE TABLE file_import (
    id             BIGSERIAL     PRIMARY KEY,
    file_name      VARCHAR(255)  NOT NULL,
    source         VARCHAR(16)   NOT NULL,
    status         VARCHAR(32)   NOT NULL,
    checksum       CHAR(64),
    total_rows     BIGINT        NOT NULL DEFAULT 0,
    accepted_rows  BIGINT        NOT NULL DEFAULT 0,
    rejected_rows  BIGINT        NOT NULL DEFAULT 0,
    error_message  VARCHAR(2000),
    started_at     TIMESTAMPTZ   NOT NULL,
    completed_at   TIMESTAMPTZ
);

CREATE INDEX idx_file_import_checksum ON file_import (checksum);
CREATE INDEX idx_file_import_started  ON file_import (started_at);

-- Rejected rows of a file counted by reason (at most one row per reason and file).
CREATE TABLE file_import_error_summary (
    id              BIGSERIAL     PRIMARY KEY,
    file_import_id  BIGINT        NOT NULL REFERENCES file_import (id) ON DELETE CASCADE,
    code            VARCHAR(32)   NOT NULL,
    row_count       BIGINT        NOT NULL,
    first_line      BIGINT        NOT NULL,
    example         VARCHAR(500)  NOT NULL,
    UNIQUE (file_import_id, code)
);

-- High/low per symbol per trading day, merged atomically from every successful file.
CREATE TABLE daily_summary (
    stock_symbol    VARCHAR(10)    NOT NULL,
    trading_date    DATE           NOT NULL,
    high_price      NUMERIC(18, 6) NOT NULL,
    high_timestamp  TIMESTAMPTZ    NOT NULL,
    low_price       NUMERIC(18, 6) NOT NULL,
    low_timestamp   TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (stock_symbol, trading_date)
);

CREATE INDEX idx_daily_summary_date ON daily_summary (trading_date);
