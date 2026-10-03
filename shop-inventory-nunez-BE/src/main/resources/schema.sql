CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    stock INT NOT NULL CHECK (stock >= 0)
);

CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL, -- CONFIRMED, REJECTED, CANCELLED
    reason VARCHAR(255),
    product_id VARCHAR(50),
    quantity INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id VARCHAR(50) NOT NULL REFERENCES inventory(product_id),
    quantity INT NOT NULL CHECK (quantity > 0)
);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS supplier_orders (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,
    buyer_ref VARCHAR(100) UNIQUE NOT NULL,
    request_id VARCHAR(100) UNIQUE NOT NULL,
    po_number VARCHAR(100),
    cases INT NOT NULL,
    units INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tiangge_cursor (
    id VARCHAR(100) PRIMARY KEY,
    cursor_value BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tiangge_processed_events (
    event_id VARCHAR(100) PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS tiangge_backorders (
    id BIGSERIAL PRIMARY KEY,
    tiangge_order_id VARCHAR(50) NOT NULL,
    shop_order_id BIGINT NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    resolution_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE
);

INSERT INTO inventory (product_id, name, stock) VALUES
('P100', 'Wireless Mouse', 20),
('P200', 'Mechanical Keyboard', 20),
('P300', 'USB-C Hub', 20)
ON CONFLICT (product_id) DO NOTHING;
