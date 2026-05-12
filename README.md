# Roadmap AI Platform

### Adaptive AI Learning & Roadmap Generation System

An intelligent, context-aware learning assistant that generates personalized developer roadmaps based on your skill level, goals, and conversation history. Tell it you're a complete beginner — it builds a beginner roadmap starting from variables and loops. Tell it you already know Spring Boot — it skips the basics and jumps straight to distributed systems.

---

## Screenshots

> _Add screenshots here after deployment_

| Dashboard | Roadmap View | Continuation |
|-----------|-------------|--------------|
| ![Dashboard](docs/screenshots/dashboard.png) | ![Roadmap](docs/screenshots/roadmap.png) | ![Chat](docs/screenshots/chat.png) |

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

| Layer    | Technology |
|----------|-----------|
| Frontend | React 19, TypeScript, Vite, Tailwind CSS, Framer Motion |
| Backend  | Java 17, Spring Boot 3.2, Spring Security, JWT |
| Database | H2 (file-based, zero setup required) |
| AI       | Groq API — llama-3.3-70b-versatile |
| Auth     | JWT access + refresh tokens |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (React/Vite)                  │
│  PromptBox → Home → ResponseRenderer → ContinuationBar   │
└────────────────────────┬────────────────────────────────┘
                         │ HTTPS REST API
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

### Adaptive AI Engine

1. User sends a message (e.g. "I know nothing about Java")
2. `SkillInferenceService` detects skill level from natural language
3. `ConversationService` persists skill level + domain to the session
4. On follow-up ("generate roadmap"), context is resolved from conversation history
5. `RoadmapStrategyEngine` builds level-appropriate constraints (forbidden topics, required topics)
6. `EnhancedAIPipeline` generates the roadmap with a focused system prompt
7. Post-generation validation rejects roadmaps that violate skill-level constraints
8. Response is returned with correct difficulty label and timeline

---

## Local Development

### Prerequisites

- Java 17+
- Node.js 18+
- A [Groq API key](https://console.groq.com/) (free tier available)

### Backend

```bash
cd backend
cp .env.example .env
# Edit .env — add your GROQ_API_KEY
./mvnw spring-boot:run
# API available at http://localhost:8080
# Health check: http://localhost:8080/health
```

### Frontend

```bash
cd frontend
npm install
cp .env.example .env
# VITE_API_BASE_URL=http://localhost:8080 (already set)
npm run dev
# App available at http://localhost:5173
```

---

## Deployment

### Frontend → Vercel

1. Import repo at [vercel.com](https://vercel.com)
2. Root Directory: `frontend` | Build: `npm run build` | Output: `dist`
3. Add env var: `VITE_API_BASE_URL` = your Render backend URL
4. Deploy — `vercel.json` handles SPA routing automatically

### Backend → Render

1. New Web Service at [render.com](https://render.com)
2. Root Directory: `backend`
3. Build: `./mvnw clean package -DskipTests`
4. Start: `java -jar target/ai-productivity-assistant-0.0.1-SNAPSHOT.jar`
5. Health Check Path: `/health`
6. Add env vars: `GROQ_API_KEY`, `JWT_SECRET`, `ALLOWED_ORIGINS` (Vercel URL), `SPRING_PROFILES_ACTIVE=prod`

See [DEPLOYMENT.md](./DEPLOYMENT.md) for full step-by-step instructions.

---

## Environment Variables

### Backend

| Variable | Required | Description |
|----------|----------|-------------|
| `GROQ_API_KEY` | ✅ | Groq API key from console.groq.com |
| `JWT_SECRET` | ✅ | Long random string for JWT signing |
| `ALLOWED_ORIGINS` | ✅ | Comma-separated allowed frontend URLs |
| `SPRING_PROFILES_ACTIVE` | No | `dev` (default) or `prod` |
| `PORT` | No | Server port (default: 8080) |

### Frontend

| Variable | Required | Description |
|----------|----------|-------------|
| `VITE_API_BASE_URL` | ✅ | Backend URL (e.g. `https://your-backend.onrender.com`) |

---

## Project Structure

```
roadmap-ai-platform/
├── backend/
│   ├── src/main/java/com/assistant/
│   │   ├── controller/        # REST endpoints
│   │   ├── service/           # AI pipeline, roadmap engine, conversation
│   │   ├── model/             # JPA entities
│   │   ├── repository/        # Spring Data repositories
│   │   ├── dto/               # Request/response DTOs
│   │   └── config/            # Security, CORS, exception handling
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
├── DEPLOYMENT.md              # Full deployment guide
├── .gitignore
└── README.md
```

---

## Future Roadmap

- [ ] PostgreSQL support for production persistence
- [ ] User profiles and learning streaks
- [ ] Roadmap sharing and export (PDF/Markdown)
- [ ] Weekly learning plan generation
- [ ] Mobile-responsive improvements
- [ ] OAuth login (Google/GitHub)
- [ ] Roadmap templates library

---

## License

MIT — see [LICENSE](./LICENSE) for details.
