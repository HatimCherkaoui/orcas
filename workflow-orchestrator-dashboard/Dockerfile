# syntax=docker/dockerfile:1
FROM node:24.20.0-alpine3.24 AS build
WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,id=orcas-npm-cache,target=/root/.npm \
    npm ci --ignore-scripts --no-audit --no-fund --prefer-offline \
    --fetch-retries=1 --fetch-timeout=30000
COPY index.html vite.config.js ./
COPY src ./src
ARG VITE_KIBANA_URL=http://localhost:5601
ENV VITE_KIBANA_URL=$VITE_KIBANA_URL
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
