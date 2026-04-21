# --- Build stage ---
FROM node:25-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npx ng build --configuration=production

# --- Serve stage ---
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/browser /usr/share/nginx/html

# SECURITY-FIX: nginx:alpine jookseb juba nginx kasutajana, aga failide
# õigused peavad olema korrektsed
RUN chown -R nginx:nginx /usr/share/nginx/html

EXPOSE 4200
