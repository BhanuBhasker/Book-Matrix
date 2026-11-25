-- One-time MySQL setup for BookMatrix. Run as a MySQL administrator (e.g. root):
--   mysql -u root -p < scripts/setup-database.sql
-- Then put the same password in .env as DB_PASSWORD.
-- The TABLES are created automatically by the application on start-up (db/schema.sql).

CREATE DATABASE IF NOT EXISTS bookmatrix_dev  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS bookmatrix_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Change the password below (MySQL may require upper/lower case, a digit and a symbol).
CREATE USER IF NOT EXISTS 'bookmatrix'@'localhost' IDENTIFIED BY 'Change-Me-123!';
CREATE USER IF NOT EXISTS 'bookmatrix'@'127.0.0.1' IDENTIFIED BY 'Change-Me-123!';

-- The app user can only touch its own two databases.
GRANT ALL PRIVILEGES ON bookmatrix_dev.*  TO 'bookmatrix'@'localhost', 'bookmatrix'@'127.0.0.1';
GRANT ALL PRIVILEGES ON bookmatrix_test.* TO 'bookmatrix'@'localhost', 'bookmatrix'@'127.0.0.1';
FLUSH PRIVILEGES;
