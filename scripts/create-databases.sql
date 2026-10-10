-- Ejecutar una vez con psql como administrador en una instalación de desarrollo.
-- No ejecutar contra bases de datos existentes de producción.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bank') THEN
        CREATE ROLE bank WITH LOGIN PASSWORD 'bank';
    END IF;
END;
$$;

SELECT 'CREATE DATABASE bank_client OWNER bank'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bank_client')\gexec
SELECT 'CREATE DATABASE bank_account OWNER bank'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bank_account')\gexec
SELECT 'CREATE DATABASE bank_corporate OWNER bank'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bank_corporate')\gexec
SELECT 'CREATE DATABASE bank_client_test OWNER bank'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bank_client_test')\gexec
SELECT 'CREATE DATABASE bank_account_test OWNER bank'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bank_account_test')\gexec
SELECT 'CREATE DATABASE bank_corporate_test OWNER bank'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bank_corporate_test')\gexec
