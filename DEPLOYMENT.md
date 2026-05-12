# Deployment Guide — AI Productivity Assistant

## Overview

| Component | Platform | Notes |
|-----------|----------|-------|
| Frontend  | Vercel   | React/Vite SPA |
| Backend   | Render or Railway | Spring Boot JAR |

---

## 1. Push to GitHub

```bash
# Initialize repo (first time)
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/roadmap-ai-platform.git
git push -u origin main
```

> **Before pushing:** verify `git status` shows no `.env` files tracked.

---

## 2. Deploy Backend to Render

### Steps
1. Go to [render.com](https://render.com) → New → Web Service
2. Connect your GitHub repository
3. Configure:
   - **Root Directory:** `backend`
   - **Build Command:** `./mvnw clean package -DskipTests`
   - **Start Command:** `java -jar target/ai-productivity-assistant-0.0.1-SNAPSHOT.jar`
   - **Health Check Path:** `/health`

### Environment Variables (set in Render dashboard)
| Variable | Value |
|----------|-------|
| `GROQ_API_KEY` | Your key from console.groq.com |
| `JWT_SECRET` | Long random string (Render can auto-generate) |
| `ALLOWED_ORIGINS` | Your Vercel frontend URL (e.g. `https://roadmap-ai.vercel.app`) |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `H2_CONSOLE_ENABLED` | `false` |

> **Note:** Free Render tier spins down after 15 min inactivity. First request after cold start takes ~30-60s.

---

## 3. Deploy Backend to Railway

### Steps
1. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
2. Select your repository
3. Railway auto-detects Java/Maven
4. Set environment variables (same as Render above)
5. Railway injects `PORT` automatically — the app reads `${PORT:8080}`

---

## 4. Deploy Frontend to Vercel

### Steps
1. Go to [vercel.com](https://vercel.com) → New Project → Import from GitHub
2. Configure:
   - **Root Directory:** `frontend`
   - **Framework Preset:** Vite
   - **Build Command:** `npm run build`
   - **Output Directory:** `dist`
3. Add environment variable:
   - `VITE_API_BASE_URL` = your Render/Railway backend URL (e.g. `https://roadmap-ai-backend.onrender.com`)
4. Deploy

---

## 5. Environment Variables Reference

### Backend
```
GROQ_API_KEY=gsk_...              # Required — Groq API key
JWT_SECRET=...                    # Required — long random string
ALLOWED_ORIGINS=https://...       # Required — your Vercel URL
SPRING_PROFILES_ACTIVE=prod       # Recommended
PORT=8080                         # Auto-set by Render/Railway
H2_CONSOLE_ENABLED=false          # Security — disable in prod
```

### Frontend
```
VITE_API_BASE_URL=https://...     # Required — your Render/Railway backend URL
```

---

## 6. Verify Deployment

After deploying, test these endpoints:

```bash
# Backend health check
curl https://your-backend.onrender.com/health
# Expected: {"status":"UP","service":"AI Productivity Assistant",...}

# Frontend
open https://your-app.vercel.app
```

---

## 7. Troubleshooting

| Problem | Solution |
|---------|----------|
| 403 on `/api/health` | Check SecurityConfig permits `/health` and `/api/health` |
| CORS errors | Set `ALLOWED_ORIGINS` to exact Vercel URL (no trailing slash) |
| 500 on roadmap generation | Check `GROQ_API_KEY` is set correctly in backend env vars |
| Frontend shows blank page | Check `VITE_API_BASE_URL` is set in Vercel dashboard |
| Cold start timeout | Normal on free Render tier — first request takes 30-60s |
| JWT errors | Ensure `JWT_SECRET` is set and consistent across restarts |

---

## 8. Local Development

```bash
# Backend
cd backend
cp .env.example .env
# Edit .env with your GROQ_API_KEY
./mvnw spring-boot:run

# Frontend (separate terminal)
cd frontend
cp .env.example .env
npm install
npm run dev
```
