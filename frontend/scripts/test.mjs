import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Keep Angular's compilation/TestBed setup while accepting Vitest's run-once flag.
const args = process.argv.slice(2).map((arg) => (arg === '--run' ? '--watch=false' : arg));
const cli = fileURLToPath(new URL('../node_modules/@angular/cli/bin/ng.js', import.meta.url));
const result = spawnSync(process.execPath, [cli, 'test', ...args], { stdio: 'inherit' });

if (result.error) {
  console.error(result.error);
}

process.exit(result.status ?? 1);
