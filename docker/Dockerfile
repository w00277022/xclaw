FROM node:22-alpine
WORKDIR /app
COPY package.json ./
RUN npm install && npm cache clean --force
COPY server.mjs ./
COPY public/ ./public/
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.mjs"]
