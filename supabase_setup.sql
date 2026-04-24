-- Zempro Supabase Setup SQL
-- Spusťte tento kód v záložce "SQL Editor" -> "New Query" v Supabase a klikněte na "RUN"

CREATE TABLE IF NOT EXISTS public.users (
    "id" text PRIMARY KEY,
    "name" text NOT NULL,
    "email" text NOT NULL,
    "pin" text NOT NULL,
    "photoUrl" text,
    "role" text NOT NULL,
    "totalHoursLogged" float8 NOT NULL DEFAULT 0.0,
    "isActive" boolean NOT NULL DEFAULT true,
    "phone" text NOT NULL DEFAULT ''
);

-- Pozor: Pokud už tabulka existuje, tento skript ji nepřepíše, takže musíte buď tabulku "tasks" nejdřív smazat (DROP TABLE tasks;) nebo použít ALTER TABLE. Tady je kompletní tabulka pro nové vytvoření:
DROP TABLE IF EXISTS public.tasks;

CREATE TABLE public.tasks (
    "id" text PRIMARY KEY,
    "title" text NOT NULL,
    "brand" text NOT NULL,
    "customerName" text NOT NULL,
    "spz" text NOT NULL,
    "vin" text NOT NULL,
    "description" text NOT NULL,
    "createdBy" text NOT NULL,
    "assignedTo" text,
    "status" text NOT NULL,
    "photoUrls" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "taskImages" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "localPhotos" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "attachedDocuments" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "timeLogs" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "electricTimeLogs" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "reworks" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "vehicleKm" int4,
    "invoiceItems" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "mechanicWorkPrice" float8 NOT NULL DEFAULT 0.0,
    "electricWorkPrice" float8 NOT NULL DEFAULT 0.0,
    "mechanicHourlyRate" float8 NOT NULL DEFAULT 0.0,
    "electricHourlyRate" float8 NOT NULL DEFAULT 0.0,
    "isInvoiceClosed" boolean NOT NULL DEFAULT false,
    "createdAt" int8 NOT NULL DEFAULT 0,
    "updatedAt" int8 NOT NULL DEFAULT 0,
    "readAt" int8,
    "startedAt" int8
);

-- Vypnutí zabezpečovací propustky po dobu vývoje pro okamžitý přístup
ALTER TABLE public.users DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.tasks DISABLE ROW LEVEL SECURITY;
