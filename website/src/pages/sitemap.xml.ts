import type { APIRoute } from 'astro';
import { routes, site } from '../content';

export const GET: APIRoute = () => new Response(
  `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${Object.values(routes).map(path => `  <url>
    <loc>${site}${path}</loc>
    <xhtml:link rel="alternate" hreflang="en" href="${site}${routes.en}"/>
    <xhtml:link rel="alternate" hreflang="es" href="${site}${routes.es}"/>
    <xhtml:link rel="alternate" hreflang="x-default" href="${site}${routes.en}"/>
  </url>`).join('\n')}
</urlset>`,
  { headers: { 'Content-Type': 'application/xml; charset=utf-8' } },
);
