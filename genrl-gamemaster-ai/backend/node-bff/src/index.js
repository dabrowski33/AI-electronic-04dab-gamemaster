import 'dotenv/config';
import express from 'express';
import { createServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import cors from 'cors';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { v4 as uuidv4 } from 'uuid';

const app = express();
const PORT = process.env.PORT || 3000;
const ORCHESTRATOR_URL = process.env.ORCHESTRATOR_URL || 'http://localhost:8000';
const SIMULATOR_URL = process.env.SIMULATOR_URL || 'http://localhost:8080';

// ─── Middleware ────────────────────────────────────────────────────────────────

app.use(cors({
  origin: ['http://localhost:5173', 'http://localhost:4200'],
  credentials: true,
}));

app.use(express.json());

// ─── Health ───────────────────────────────────────────────────────────────────

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// ─── Proxy Routes ─────────────────────────────────────────────────────────────

app.use('/api/orchestrator', createProxyMiddleware({
  target: `${ORCHESTRATOR_URL}/api/v1`,
  changeOrigin: true,
  pathRewrite: { '^/api/orchestrator': '' },
  on: {
    error: (err, _req, res) => {
      console.error('[proxy/orchestrator] error:', err.message);
      res.status(502).json({ error: 'Orchestrator unavailable', detail: err.message });
    },
  },
}));

app.use('/api/simulator', createProxyMiddleware({
  target: `${SIMULATOR_URL}/api/simulator`,
  changeOrigin: true,
  pathRewrite: { '^/api/simulator': '' },
  on: {
    error: (err, _req, res) => {
      console.error('[proxy/simulator] error:', err.message);
      res.status(502).json({ error: 'Simulator unavailable', detail: err.message });
    },
  },
}));

// ─── Mock experiment store (in-memory, development only) ──────────────────────

/** @type {Map<string, object>} */
const experiments = new Map();

app.get('/api/experiments', (_req, res) => {
  res.json(Array.from(experiments.values()));
});

app.post('/api/experiments', (req, res) => {
  const id = uuidv4();
  const experiment = {
    id,
    status: 'running',
    generation: 0,
    best_fitness: 0,
    avg_fitness: 0,
    created_at: new Date().toISOString(),
    ...req.body,
  };
  experiments.set(id, experiment);
  console.log('[experiments] created:', id);
  res.status(201).json(experiment);
});

// ─── HTTP server + WebSocket ──────────────────────────────────────────────────

const server = createServer(app);

const wss = new WebSocketServer({ server, path: '/ws' });

/** @type {Set<WebSocket>} */
const clients = new Set();

wss.on('connection', (ws, req) => {
  const clientId = uuidv4();
  clients.add(ws);
  console.log(`[ws] client connected: ${clientId} (total: ${clients.size})`);

  ws.send(JSON.stringify({
    type: 'connected',
    clientId,
    timestamp: new Date().toISOString(),
  }));

  ws.on('message', (raw) => {
    try {
      const msg = JSON.parse(raw.toString());
      console.log('[ws] message from client:', msg.type);

      if (msg.type === 'subscribe_experiment') {
        ws.experimentId = msg.experimentId;
        ws.send(JSON.stringify({
          type: 'subscribed',
          experimentId: msg.experimentId,
          timestamp: new Date().toISOString(),
        }));
      }
    } catch (err) {
      console.warn('[ws] invalid message:', err.message);
    }
  });

  ws.on('close', () => {
    clients.delete(ws);
    console.log(`[ws] client disconnected: ${clientId} (total: ${clients.size})`);
  });

  ws.on('error', (err) => {
    console.error(`[ws] error for ${clientId}:`, err.message);
    clients.delete(ws);
  });
});

/**
 * Broadcast a JSON event to all connected WebSocket clients.
 * Optionally filter by experimentId if the event carries one.
 * @param {object} event
 */
function broadcast(event) {
  const payload = JSON.stringify(event);
  for (const client of clients) {
    if (client.readyState !== WebSocket.OPEN) continue;
    // If the client has subscribed to a specific experiment, filter
    if (client.experimentId && event.experimentId && client.experimentId !== event.experimentId) continue;
    client.send(payload);
  }
}

// ─── Mock event emitter ───────────────────────────────────────────────────────

let mockTick = 0;

setInterval(() => {
  if (clients.size === 0) return;

  // Advance every running experiment
  for (const [id, exp] of experiments) {
    if (exp.status !== 'running') continue;

    exp.generation += 1;
    const noise = () => (Math.random() - 0.3) * 2;
    exp.best_fitness = Math.min(100, exp.best_fitness + Math.max(0, 0.8 + noise()));
    exp.avg_fitness = Math.min(exp.best_fitness, exp.avg_fitness + Math.max(0, 0.5 + noise()));

    broadcast({
      type: 'generation_update',
      experimentId: id,
      generation: exp.generation,
      best_fitness: parseFloat(exp.best_fitness.toFixed(4)),
      avg_fitness: parseFloat(exp.avg_fitness.toFixed(4)),
      population_size: exp.population_size || 50,
      timestamp: new Date().toISOString(),
    });
  }

  // Global heartbeat every ~10 s (5 * 2 s interval)
  mockTick += 1;
  if (mockTick % 5 === 0) {
    broadcast({
      type: 'heartbeat',
      activeExperiments: Array.from(experiments.values()).filter(e => e.status === 'running').length,
      connectedClients: clients.size,
      timestamp: new Date().toISOString(),
    });
  }

  // Mutation event ~every 6 s
  if (mockTick % 3 === 0) {
    for (const [id, exp] of experiments) {
      if (exp.status !== 'running') continue;
      const mutationTypes = ['weight_perturbation', 'topology_change', 'learning_rate_adjust', 'activation_swap'];
      broadcast({
        type: 'mutation_triggered',
        experimentId: id,
        generation: exp.generation,
        mutation_type: mutationTypes[Math.floor(Math.random() * mutationTypes.length)],
        reasoning: 'Stagnacja wykryta — LRM zaproponował mutację topologii sieci neuronowej.',
        validation_status: Math.random() > 0.2 ? 'approved' : 'rejected',
        timestamp: new Date().toISOString(),
      });
    }
  }

  // Sandbox result ~every 8 s
  if (mockTick % 4 === 0) {
    for (const [id, exp] of experiments) {
      if (exp.status !== 'running') continue;
      broadcast({
        type: 'sandbox_result',
        experimentId: id,
        generation: exp.generation,
        score: parseFloat((Math.random() * 100).toFixed(2)),
        status: Math.random() > 0.1 ? 'success' : 'crash',
        timestamp: new Date().toISOString(),
      });
    }
  }
}, 2000);

// ─── Start ────────────────────────────────────────────────────────────────────

server.listen(PORT, () => {
  console.log(`[bff] GenRL Node BFF running on http://localhost:${PORT}`);
  console.log(`[bff]  → Orchestrator proxy : ${ORCHESTRATOR_URL}/api/v1`);
  console.log(`[bff]  → Simulator proxy    : ${SIMULATOR_URL}/api/simulator`);
  console.log(`[bff]  → WebSocket          : ws://localhost:${PORT}/ws`);
});
