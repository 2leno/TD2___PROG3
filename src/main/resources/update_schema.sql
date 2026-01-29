DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_type_enum') THEN
        CREATE TYPE order_type_enum AS ENUM ('EAT_IN', 'TAKE_AWAY');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_status_enum') THEN
        CREATE TYPE order_status_enum AS ENUM ('CREATED', 'READY', 'DELIVERED');
    END IF;
END $$;

ALTER TABLE "order"
ADD COLUMN IF NOT EXISTS order_type order_type_enum,
ADD COLUMN IF NOT EXISTS status order_status_enum DEFAULT 'CREATED';

UPDATE "order" SET status = 'CREATED' WHERE status IS NULL;