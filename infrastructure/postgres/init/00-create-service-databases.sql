-- Local-development bootstrap only. Production credentials must come from a secret manager.
-- This script runs once when the PostgreSQL volume is empty.

CREATE ROLE identity_user LOGIN PASSWORD 'identity_local_password';
CREATE DATABASE identity_db OWNER identity_user;

CREATE ROLE customer_user LOGIN PASSWORD 'customer_local_password';
CREATE DATABASE customer_db OWNER customer_user;

CREATE ROLE inventory_user LOGIN PASSWORD 'inventory_local_password';
CREATE DATABASE inventory_db OWNER inventory_user;

CREATE ROLE reservation_user LOGIN PASSWORD 'reservation_local_password';
CREATE DATABASE reservation_db OWNER reservation_user;

CREATE ROLE pricing_user LOGIN PASSWORD 'pricing_local_password';
CREATE DATABASE pricing_db OWNER pricing_user;

CREATE ROLE payment_user LOGIN PASSWORD 'payment_local_password';
CREATE DATABASE payment_db OWNER payment_user;

CREATE ROLE stay_user LOGIN PASSWORD 'stay_local_password';
CREATE DATABASE stay_db OWNER stay_user;

CREATE ROLE service_catalog_user LOGIN PASSWORD 'service_catalog_local_password';
CREATE DATABASE service_catalog_db OWNER service_catalog_user;

CREATE ROLE billing_user LOGIN PASSWORD 'billing_local_password';
CREATE DATABASE billing_db OWNER billing_user;

CREATE ROLE review_user LOGIN PASSWORD 'review_local_password';
CREATE DATABASE review_db OWNER review_user;

CREATE ROLE notification_user LOGIN PASSWORD 'notification_local_password';
CREATE DATABASE notification_db OWNER notification_user;

CREATE ROLE reporting_audit_user LOGIN PASSWORD 'reporting_audit_local_password';
CREATE DATABASE reporting_audit_db OWNER reporting_audit_user;

CREATE ROLE ai_conversation_user LOGIN PASSWORD 'ai_conversation_local_password';
CREATE DATABASE ai_conversation_db OWNER ai_conversation_user;

CREATE ROLE ai_recommendation_user LOGIN PASSWORD 'ai_recommendation_local_password';
CREATE DATABASE ai_recommendation_db OWNER ai_recommendation_user;

CREATE ROLE ai_forecasting_user LOGIN PASSWORD 'ai_forecasting_local_password';
CREATE DATABASE ai_forecasting_db OWNER ai_forecasting_user;

CREATE ROLE ai_revenue_user LOGIN PASSWORD 'ai_revenue_local_password';
CREATE DATABASE ai_revenue_db OWNER ai_revenue_user;

CREATE ROLE ai_customer_analytics_user LOGIN PASSWORD 'ai_customer_analytics_local_password';
CREATE DATABASE ai_customer_analytics_db OWNER ai_customer_analytics_user;

CREATE ROLE legacy_user LOGIN PASSWORD 'legacy_local_password';
CREATE DATABASE hotel_legacy_db OWNER legacy_user;

-- Extensions must be installed by the PostgreSQL bootstrap superuser. Installing
-- pgvector here lets the application owner run Flyway without superuser rights.
\connect hotel_legacy_db
CREATE EXTENSION IF NOT EXISTS vector;

\connect ai_conversation_db
CREATE EXTENSION IF NOT EXISTS vector;
