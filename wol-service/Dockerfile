FROM node:20-alpine
WORKDIR /app
COPY server.js /app/server.js
ENV WOL_PORT=8790
ENV WOL_DATA_FILE=/data/pcs.json
EXPOSE 8790
VOLUME ["/data"]
CMD ["node", "/app/server.js"]
