CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    stock INT NOT NULL CHECK (stock >= 0)
);

CREATE TABLE IF NOT EXISTS orders (
    order_id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL, -- CONFIRMED, REJECTED, CANCELLED
    reason VARCHAR(255),
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

INSERT INTO inventory (product_id, name, stock) VALUES
('P100', 'Wireless Mouse', 20),
('P200', 'Mechanical Keyboard', 20),
('P300', 'USB-C Hub', 20)
ON CONFLICT (product_id) DO UPDATE SET stock = EXCLUDED.stock, name = EXCLUDED.name;