# Adaptive AI Learning & Roadmap Platform

An intelligent, context-aware learning assistant that generates personalized developer roadmaps based on your skill level, goals, and conversation history.

Tell it you're a complete beginner — it builds a beginner roadmap starting from variables and loops.  
Tell it you already know Spring Boot — it skips the basics and jumps straight to distributed systems.

---

## Features

- **Adaptive roadmap generation** — roadmaps that match your actual skill level (beginner / intermediate / advanced)
- **Context-aware conversations** — the AI remembers what you said earlier and uses it to personalize every response
- **Skill-level detection** — automatically detects beginner, intermediate, or advanced signals from natural language
- **Beginner safety** — beginner roadmaps never include Kubernetes, Kafka, JWT, or microservices
- **Multiple intent types** — roadmaps, concept explanations, code help, analysis, productivity plans
- **Roadmap history** — all generated roadmaps are saved and browsable
- **Step tracking** — mark steps complete, add notes, track progress
- **Follow-up actions** — expand a step, generate a project, get interview questions, quiz yourself
- **Continuation bar** — continue any roadmap conversation with context preserved

---

## Tech Stack

| Layer    | Technology                        |
|----------|-----------------------------------|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS |
| Backend  | Java 17, Spring Boot 3.2, Spring Security |
| Database | H2 (file-based, no setup required) |
| AI       | Groq API (llama-3.3-70b-versatile) |
| Auth     | JWT (access + refresh tokens) |

---

## Local Development

### Prerequisites

- Java 17+
- Node.js 18+
- A [Groq API key](https://console.groq.com/) (free tier available)

### 1. Clone the repository

```bash
git clone https://github.com/your-username/roadmap-ai-platform.git
cd roadmap-ai-platform
```

### 2. Backend setup

```bash
cd backend

# Copy the example env file
cp .env.example .env

# Edit .env and add your Groq API key
# GROQ_API_KEY=your_key_here

# Run the backend (uses H2 file database — no external DB needed)
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 3. Frontend setup

```bash
cd frontend

# Install dependencies
npm install

# Copy the example env file
cp .env.example .env
# VITE_API_BASE_URL=http://localhost:8080 (already set)

# Start the dev server
npm run dev
```

The app will be available at `http://localhost:5173`.

---

## Environment Variables

### Backend

| Variable | Required | Description |
|----------|----------|-------------|
| `GROQ_API_KEY` | ✅ Yes | Your Groq API key from console.groq.com |
| `JWT_SECRET` | ✅ Yes (prod) | Long random string for JWT signing |
| `ALLOWED_ORIGINS` | ✅ Yes (prod) | Comma-separated list of allowed frontend URLs |
| `SPRING_PROFILES_ACTIVE` | No | `dev` (default) or `prod` |
| `PORT` | No | Server port (default: 8080) |

### Frontend

| Variable | Required | Description |
|----------|----------|-------------|
| `VITE_API_BASE_URL` | ✅ Yes | URL of the deployed backend |

---

## Deployment

### Frontend → Vercel

1. Push your code to GitHub
2. Import the repository in [Vercel](https://vercel.com)
3. Set **Root Directory** to `frontend`
4. Set **Build Command** to `npm run build`
5. Set **Output Directory** to `dist`
6. Add environment variable: `VITE_API_BASE_URL` = your Render backend URL
7. Deploy

### Backend → Render

1. Create a new **Web Service** in [Render](https://render.com)
2. Connect your GitHub repository
3. Set **Root Directory** to `backend`
4. Set **Build Command** to `./mvnw clean package -DskipTests`
5. Set **Start Command** to `java -jar target/ai-productivity-assistant-0.0.1-SNAPSHOT.jar`
6. Add environment variables:
   - `GROQ_API_KEY` — your Groq API key
   - `JWT_SECRET` — a long random string
   - `ALLOWED_ORIGINS` — your Vercel frontend URL
   - `SPRING_PROFILES_ACTIVE` — `prod`
7. Deploy

> **Note:** The free Render tier spins down after inactivity. The first request after a cold start may take 30–60 seconds.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (React/Vite)                  │
│  PromptBox → Home → ResponseRenderer → ContinuationBar   │
└────────────────────────┬────────────────────────────────┘
                         │ HTTPS (REST API)
┌────────────────────────▼────────────────────────────────┐
│                  Backend (Spring Boot)                    │
│                                                           │
│  TaskController → TaskService → EnhancedAIPipeline       │
│                       ↓                                   │
│  ConversationService (session context + history)          │
│                       ↓                                   │
│  SkillInferenceService → RoadmapStrategyEngine            │
│                       ↓                                   │
│  GroqClient → Groq API (llama-3.3-70b)                   │
│                       ↓                                   │
│  H2 Database (tasks, conversations, roadmaps)             │
└───────────────────────────────────────────────────────────┘
```

### How adaptive generation works

1. User sends a message (e.g. "I know nothing about Java")
2. `SkillInferenceService` detects skill level from natural language signals
3. `ConversationService` persists skill level + domain to the session
4. On follow-up ("generate roadmap"), context is resolved from conversation history
5. `RoadmapStrategyEngine` builds level-appropriate constraints (forbidden topics, required topics)
6. `EnhancedAIPipeline` generates the roadmap with a single focused system prompt
7. Post-generation validation rejects roadmaps that violate skill-level constraints
8. Response is returned with correct difficulty label and timeline

---

## Project Structure

```
roadmap-ai-platform/
├── backend/
│   ├── src/main/java/com/assistant/
│   │   ├── controller/        # REST endpoints
│   │   ├── service/           # Business logic + AI pipeline
│   │   ├── model/             # JPA entities
│   │   ├── repository/        # Spring Data repositories
│   │   ├── dto/               # Request/response DTOs
│   │   └── config/            # Security, CORS, exception handling
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── .env.example
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── pages/             # Home page
│   │   ├── components/        # UI components + renderers
│   │   ├── hooks/             # Custom React hooks
│   │   ├── services/          # API client
│   │   └── types/             # TypeScript types
│   ├── .env.example
│   ├── vercel.json
│   └── package.json
├── render.yaml                # Render deployment config
├── .gitignore
└── README.md
```

---

## Suggested Repository Name

`roadmap-ai-platform` or `adaptive-learning-roadmap`

---

## License

MIT
