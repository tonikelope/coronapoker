import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://coronapoker.net',
  output: 'static',
  trailingSlash: 'always',
  telemetry: false,
});
