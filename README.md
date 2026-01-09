# Hazle Backend

This is the Kotlin-based backend for **Hazle**, an AI-powered assistant. It acts as the orchestration layer between the Android client, the OpenAI API, and Supabase for data management.

> **Note:** This is the server-side repository. The Android client can be found at [hazle-android](https://github.com/tashilapathum/hazle-android).

---

## 🏗 System Architecture

The backend is built with a focus on high performance and asynchronous communication, managing the following core flows:

* **Authentication:** Validates users via JWT and coordinates with Supabase Auth.
* **AI Orchestration:** Processes text context from the mobile app through OpenAI's models.
* **Data Persistence:** Manages user data and conversation history using Supabase (PostgreSQL).

---

## 🛠 Tech Stack

### Server & Networking

* **Ktor (Netty):** A high-performance, asynchronous server engine for Kotlin.
* **Content Negotiation:** Seamless JSON serialization using `kotlinx.serialization`.
* **Rate Limiting & Status Pages:** Robust error handling and request throttling for production stability.

### Intelligence & Data

* **OpenAI SDK:** Integrated for processing natural language tasks.
* **Supabase Kotlin:** Direct integration with Postgrest (database) and GoTrue (auth).
* **JWT Auth:** Secure, stateless authentication for the Android client.

---

## ⚙️ Key Features

* **Background AI Processing:** Receives context-menu text from Android and returns structured AI insights.
* **Secure Data Sync:** Keeps conversation history in sync across devices using Supabase.
* **Scalable Architecture:** Built on Coroutines to handle high concurrency with minimal resource overhead.

---

## 🚀 Getting Started

1. **Clone the repository:**
```bash
git clone https://github.com/tashilapathum/hazle-backend.git

```


2. **Configuration & Environment Variables**

To run the backend, create a `.env` file in the root directory or set the following variables in your environment:

| Variable | Description |
| --- | --- |
| `ASSISTANT_NAME` | Display name for the AI assistant. |
| `ASSITANT_INSTRUCTIONS` | System prompt/behavior instructions for the AI. |
| `KTOR_ENV` | Server environment (e.g., `dev` or `prod`). |
| `OPENAI_API_KEY` | Your OpenAI API key. |
| `OPENAI_MODEL_ID` | The specific model to use (e.g., `gpt-4o`). |
| `SENTRY_AUTH_TOKEN` | Authentication token for Sentry error tracking. |
| `SUPABASE_URL` | Your Supabase project URL. |
| `SUPABASE_ANON_KEY` | The anonymous public key for Supabase. |
| `SUPABASE_SERVICE_ROLE_KEY` | Secret service role key for admin-level database access. |
| `SUPABASE_JWT_SECRET` | The secret used to verify JWT tokens from Supabase Auth. |

---


3. **Run with Gradle:**
```bash
./gradlew run

```
