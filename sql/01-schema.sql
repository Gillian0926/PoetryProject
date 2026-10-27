--
-- PostgreSQL database dump
--

\restrict Qn9zabcZQCa8fdJYo8FF5k19pJDEJfwPMSA1racRaJbnAxvriF2ID5bx7YmQdHB

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg12+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg12+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION vector IS 'vector data type and ivfflat and hnsw access methods';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ai_analysis_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_analysis_cache (
    cache_key character varying(255) NOT NULL,
    content text
);


--
-- Name: history_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.history_events (
    id integer NOT NULL,
    year integer NOT NULL,
    event_name text,
    description text
);


--
-- Name: history_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.history_events_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: history_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.history_events_id_seq OWNED BY public.history_events.id;


--
-- Name: poem_details_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.poem_details_cache (
    cache_key character varying(255) NOT NULL,
    data jsonb
);


--
-- Name: poems; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.poems (
    id integer NOT NULL,
    title text,
    author text,
    content text,
    dynasty text,
    original_id text,
    created_time text,
    author_simple text
);


--
-- Name: poet_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.poet_profiles (
    author character varying(255) NOT NULL,
    dynasty character varying(50),
    poem_count integer,
    style_vector public.vector(1536)
);


--
-- Name: tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tags (
    id integer NOT NULL,
    poem_id integer,
    flower text,
    emotion text,
    tag text,
    embedding public.vector(1536)
);


--
-- Name: term_embeddings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.term_embeddings (
    term character varying(255) NOT NULL,
    embedding public.vector(1536)
);


--
-- Name: history_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.history_events ALTER COLUMN id SET DEFAULT nextval('public.history_events_id_seq'::regclass);


--
-- Name: ai_analysis_cache ai_analysis_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_analysis_cache
    ADD CONSTRAINT ai_analysis_cache_pkey PRIMARY KEY (cache_key);


--
-- Name: history_events history_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.history_events
    ADD CONSTRAINT history_events_pkey PRIMARY KEY (id);


--
-- Name: poem_details_cache poem_details_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.poem_details_cache
    ADD CONSTRAINT poem_details_cache_pkey PRIMARY KEY (cache_key);


--
-- Name: poems poems_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.poems
    ADD CONSTRAINT poems_pkey PRIMARY KEY (id);


--
-- Name: poet_profiles poet_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.poet_profiles
    ADD CONSTRAINT poet_profiles_pkey PRIMARY KEY (author);


--
-- Name: tags tags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT tags_pkey PRIMARY KEY (id);


--
-- Name: term_embeddings term_embeddings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.term_embeddings
    ADD CONSTRAINT term_embeddings_pkey PRIMARY KEY (term);


--
-- Name: idx_poems_author_simple; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_poems_author_simple ON public.poems USING btree (author_simple);


--
-- Name: tags_embedding_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tags_embedding_idx ON public.tags USING ivfflat (embedding public.vector_cosine_ops) WITH (lists='100');


--
-- Name: tags tags_poem_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tags
    ADD CONSTRAINT tags_poem_id_fkey FOREIGN KEY (poem_id) REFERENCES public.poems(id);


--
-- PostgreSQL database dump complete
--

\unrestrict Qn9zabcZQCa8fdJYo8FF5k19pJDEJfwPMSA1racRaJbnAxvriF2ID5bx7YmQdHB

