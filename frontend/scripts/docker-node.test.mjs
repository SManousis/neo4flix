import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';

// Resolve the CLI's own semver dependency without adding a new dependency pin.
const require = createRequire(new URL('../node_modules/@angular/cli/package.json', import.meta.url));
const { satisfies } = require('semver');

test('Docker build pins Node 24 compatible with the locked Angular toolchain', () => {
  const dockerfile = readFileSync(new URL('../Dockerfile', import.meta.url), 'utf8');
  const match = dockerfile.match(/^FROM node:(24\.\d+\.\d+)-alpine\d+\.\d+ AS build\r?$/m);
  assert.ok(match, 'Docker build must pin an exact Node 24 and Alpine version');
  const version = match[1];
  const lock = JSON.parse(readFileSync(new URL('../package-lock.json', import.meta.url), 'utf8'));

  for (const name of ['@angular/cli', '@angular/build', '@angular/compiler-cli', '@angular/core']) {
    const range = lock.packages[`node_modules/${name}`].engines.node;
    assert.ok(satisfies(version, range), `Docker Node ${version} does not satisfy ${name}: ${range}`);
  }
});
