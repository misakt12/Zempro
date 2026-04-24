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

CREATE TABLE IF NOT EXISTS public.tasks (
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
    "reworks" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "vehicleKm" int4,
    "invoiceItems" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "mechanicWorkPrice" float8 NOT NULL DEFAULT 0.0,
    "electricWorkPrice" float8 NOT NULL DEFAULT 0.0,
    "isInvoiceClosed" boolean NOT NULL DEFAULT false,
    "createdAt" int8 NOT NULL DEFAULT 0,
    "updatedAt" int8 NOT NULL DEFAULT 0
);

-- Zapnutí Realtime poslouchání (aby appka poznala, když se zapíše nová zakázka)
ALTER PUBLICATION supabase_realtime ADD TABLE public.tasks;
ALTER PUBLICATION supabase_realtime ADD TABLE public.users;

-- Vypnutí RLS po dobu vývoje pro okamžitý přístup (volitelné)
ALTER TABLE public.users DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.tasks DISABLE ROW LEVEL SECURITY;
