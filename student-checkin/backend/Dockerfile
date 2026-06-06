FROM node:20-bookworm-slim

WORKDIR /app

COPY backend/package.json ./backend/package.json
RUN cd backend && npm install --omit=dev

COPY backend ./backend
COPY supabase ./supabase

ENV NODE_ENV=production
ENV STORAGE_ROOT=/app/data/storage/task-deliveries

EXPOSE 8000

CMD ["node", "backend/src/server.js"]
