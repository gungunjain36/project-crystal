# Crystal Client

A React web application for configuring GitHub repositories and triggering security scans via the Crystal security scanner.

## What it does

- **Dashboard**: Grid of configured repos with quick stats (total scans, critical/high issue counts)
- **Repo management**: Add/remove GitHub repo URLs stored in browser localStorage
- **Trigger scans**: One-click "Run Scan" per configured repository
- **Results view**: Paginated table of all past scan results with severity breakdowns
- **Job detail**: Full findings table for a single scan job with severity summary cards
- **Real-time polling**: Auto-refreshes job status every 3 seconds until scan completes

## Setup

```bash
npm install
cp .env.example .env
npm run dev
```

Open http://localhost:5173

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `VITE_GATEWAY_URL` | `http://localhost:8080` | URL of the ms-gateway service |
| `VITE_API_KEY` | `dev-secret-key` | API key sent as `X-API-Key` header |

## Repository Management

Repositories are stored client-side in `localStorage` under the key `crystal_repos`. No backend is required for repo management — repos persist across browser refreshes automatically.

Each repo entry stores: `{ id, url, addedAt, lastJobId, lastStatus, lastResult }`

## UI Overview

- **Dark theme**: `bg-gray-900` background, `bg-gray-800` cards
- **Severity colors**: Critical=red, High=orange, Medium=yellow, Low=green
- **Sidebar navigation**: Dashboard and Scan Results pages
- **Responsive grid**: Repo cards in 1/2/3 column layouts depending on viewport
- Job IDs and file paths rendered in `font-mono` for readability

## Docker

```bash
docker build -t crystal-client .
docker run -p 80:80 crystal-client
```

The nginx config proxies `/api/` requests to `http://ms-gateway:8080`.
