FROM node:24.15.0-alpine3.22 AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/angular.json frontend/tsconfig*.json ./
COPY frontend/src/ src/
COPY frontend/public/ public/
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28.0-alpine
COPY --from=build /app/dist/frontend/browser/ /usr/share/nginx/html/
COPY infra/nginx/default.conf /etc/nginx/conf.d/default.conf
USER 101
EXPOSE 8080
