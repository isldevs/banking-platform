#!/usr/bin/env node
/** Run the API collection without Postman environments or secret-bearing reports. */
import { createRequire } from 'node:module';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const defaults = {
  collection: resolve(root, 'docs/postman/banking-platform.postman_collection.json'),
  appConfig: resolve(root, 'infrastructure/.env'),
  summary: resolve(root, '.local/postman-summary.json'),
};

function options(argv) {
  const result = { ...defaults };
  const names = new Map([
    ['--collection', 'collection'], ['--app-config', 'appConfig'],
    ['--env-file', 'appConfig'], ['--summary', 'summary'],
  ]);
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--help' || argv[i] === '-h') {
      console.log('Usage: node infrastructure/scripts/run-postman.mjs [--collection FILE] [--app-config FILE] [--summary FILE]');
      console.log('The app config is the server .env, not a Postman environment. All Newman inputs remain collection variables.');
      return null;
    }
    const name = names.get(argv[i]);
    if (!name || !argv[i + 1] || argv[i + 1].startsWith('--')) {
      throw new Error('Unknown option or missing value. Use --help for supported options.');
    }
    result[name] = resolve(argv[++i]);
  }
  return result;
}

function appSettings(text) {
  const values = new Map();
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const match = /^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/.exec(line);
    if (!match) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    } else {
      value = value.replace(/\s+#.*$/, '').trim();
    }
    values.set(match[1], value);
  }
  return values;
}

function populateCollection(collection, values) {
  if (!Array.isArray(collection.variable)) throw new Error('The collection must declare its variables.');
  const declared = new Map(collection.variable.map(variable => [variable.key, variable]));
  const mappings = {
    admin_username: values.get('DEMO_ADMIN_USERNAME'),
    admin_password: values.get('DEMO_ADMIN_PASSWORD'),
    internal_service_key: values.get('INTERNAL_SERVICE_KEY'),
    oauth_client_secret: values.get('OAUTH2_DEMO_CLIENT_SECRET'),
    expected_issuer: values.get('DOCKER_JWT_ISSUER') || values.get('SECURITY_JWT_ISSUER'),
  };
  for (const [key, value] of Object.entries(mappings)) {
    if (!declared.has(key)) throw new Error(`The collection is missing variable ${key}.`);
    if (value !== undefined && value !== '') declared.get(key).value = value;
  }
  for (const key of ['admin_username', 'admin_password', 'internal_service_key', 'oauth_client_secret', 'expected_issuer']) {
    const value = String(declared.get(key)?.value ?? '');
    if (!value || value.startsWith('replace-with-') || /^<.*>$/.test(value)) {
      throw new Error(`Set collection variable ${key} or supply its server app configuration value.`);
    }
  }
  // Do not construct Newman environment, globals or iteration-data objects.
  return collection;
}

async function main() {
  const args = options(process.argv.slice(2));
  if (!args) return;
  const collection = JSON.parse(await readFile(args.collection, 'utf8'));
  const appConfig = existsSync(args.appConfig) ? appSettings(await readFile(args.appConfig, 'utf8')) : new Map();
  populateCollection(collection, appConfig);

  let newman;
  try {
    const require = createRequire(resolve(root, 'docs/postman/package.json'));
    newman = require('newman');
  } catch {
    throw new Error('Newman is not installed. Run npm ci --prefix docs/postman first.');
  }

  const statuses = [];
  const violations = [];
  const startedAt = new Date().toISOString();
  const summary = await new Promise((accept, reject) => {
    const execution = newman.run({
      collection,
      reporters: [],
      timeoutRequest: 15000,
      timeoutScript: 20000,
      timeout: 600000,
      delayRequest: 100,
      insecure: false,
      bail: false,
    }, (error, result) => error ? reject(new Error('Newman could not complete the collection run.')) : accept(result));

    execution.on('request', (error, args) => {
      const name = args.item?.name || 'Unnamed request';
      const status = args.response?.code ?? null;
      statuses.push({ name, status });
      if (error || status === null || status < 200 || status >= 400) {
        violations.push({ request: name, kind: error ? 'RequestError' : 'UnexpectedHttpStatus', status });
      }
      console.log(`${status ?? 'ERROR'} ${name}`);
    });
    // Sandbox console messages are intentionally not forwarded: tokens may be present there.
  });

  const failures = (summary.run?.failures || []).map(failure => ({
    request: failure.source?.name || 'Collection script',
    kind: failure.error?.name || 'AssertionError',
    assertion: failure.error?.test || failure.error?.assertion || 'Request or script check',
  }));
  const report = {
    startedAt,
    finishedAt: new Date().toISOString(),
    collection: collection.info?.name || 'Banking API collection',
    requests: statuses.length,
    assertions: summary.run?.stats?.assertions?.total || 0,
    failedAssertions: summary.run?.stats?.assertions?.failed || 0,
    successful: failures.length === 0 && violations.length === 0,
    requestStatuses: statuses,
    failures,
    httpFailures: violations,
  };
  await mkdir(dirname(args.summary), { recursive: true });
  await writeFile(args.summary, JSON.stringify(report, null, 2) + '\n', { mode: 0o600 });
  console.log(`${report.requests} requests, ${report.assertions} assertions, ${report.failedAssertions} failed assertions.`);
  for (const failure of failures) console.error(`FAIL ${failure.request}: ${failure.kind} (${failure.assertion})`);
  console.log(report.successful ? 'Collection passed: every request and assertion succeeded.' : 'Collection failed; inspect the sanitized summary for request names and assertion labels.');
  console.log(`Sanitized summary: ${args.summary}`);
  if (!report.successful) process.exitCode = 1;
}

main().catch(() => {
  // Avoid logging exception details that could contain a URL, token or request payload.
  console.error('API collection could not run. Check the collection file, required collection variables and Newman installation. Use --help for usage.');
  process.exitCode = 1;
});
