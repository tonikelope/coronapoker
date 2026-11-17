# Web de CoronaPoker

Web estática en Astro. Inglés en `/` y español en `/es/`. Al abrir la raíz, la web elige el idioma a partir del navegador. `?lang=en` y `?lang=es` permiten elegirlo explícitamente. La compilación de producción queda en `dist/`.

## Probar en local

Usa Node.js 24 LTS. La compilación se ha probado con Node.js 24.19.0.

Desde la raíz del repositorio, en PowerShell:

```powershell
cd website
npm ci
$env:ASTRO_TELEMETRY_DISABLED = '1'
npm run dev
```

Abre [la versión inglesa](http://127.0.0.1:4321/) o [la española](http://127.0.0.1:4321/es/). El terminal indica la dirección si el puerto predeterminado está ocupado. Pulsa Ctrl+C para cerrar el servidor.

Para probar la compilación de producción:

```powershell
$env:ASTRO_TELEMETRY_DISABLED = '1'
npm run build
npm run preview
```

## Contenido y recursos

- `src/content.ts` reúne textos, rutas, enlaces y el inventario de capturas. `src/components/Site.astro` compone la página y sus metadatos. `MentalPoker.astro` contiene los diagramas en tres pasos.
- `src/styles/site.css` contiene el diseño. `src/scripts/site.ts` añade navegación a la galería y permite avanzar o retroceder en el reparto de Mental Poker. Sin JavaScript, las capturas se abren como archivos y el reparto se explica junto al diagrama.
- Para ampliar idiomas, añade los textos y la URL en `src/content.ts`, crea la página correspondiente en `src/pages/` e incorpora ese idioma al selector y a los metadatos.
- El logotipo oficial procede de `src/main/resources/images/coronapoker_logo_big.png`. El favicon reutiliza `packaging/windows/CoronaPoker.ico`.
- Nueve capturas del juego en `public/media/`. La galería sirve WebP adaptativos y abre cada captura a su resolución original. Las interfaces de las capturas conservan sus idiomas de origen.
- La portada se generó con ImageGen. El encargo visual fue una sala nocturna de póker con tapete granate, fichas y dos ases a la derecha, con espacio oscuro a la izquierda para el logotipo. En la segunda pasada se afinaron ambos ases y se corrigieron las sombras de contacto, conservando luz, encuadre y fichas. Sus variantes `hero-*.webp` están en `public/media/`.
- Inter se sirve localmente. Su licencia SIL OFL está en `public/licenses/`.
- La descripción del juego se basa en el README y en las especificaciones de [seguridad](https://github.com/tonikelope/coronapoker/blob/master/docs/SECURITY.md), [bots](https://github.com/tonikelope/coronapoker/blob/master/docs/BOTS.md) y [paquetes MOD](https://github.com/tonikelope/coronapoker/blob/master/docs/MODS.md). Las descargas apuntan a la última versión de GitHub Releases.

## Cloudflare Pages después de aprobar la web en local

1. Sube `website/` a la rama de GitHub que quieras desplegar.
2. En Cloudflare, entra en **Workers & Pages → Create application → Pages → Connect to Git**. Selecciona `tonikelope/coronapoker` y la rama aprobada como rama de producción.
3. Configura el proyecto con estos valores.

   | Campo | Valor |
   | --- | --- |
   | Framework preset | Astro |
   | Root directory | `website` |
   | Build command | `npm run build` |
   | Build output directory | `dist` |
   | Variable de compilación | `NODE_VERSION=24.19.0` |
   | Variable de compilación | `ASTRO_TELEMETRY_DISABLED=1` |

4. Ejecuta **Save and Deploy** cuando quieras publicar. Comprueba ambas rutas en el dominio de Pages.
5. Añade `coronapoker.net` como zona de Cloudflare en la misma cuenta que el proyecto de Pages. Si vas a cambiar los servidores de nombres en el registrador, conserva los registros DNS vigentes de la zona antes del cambio.
6. En el proyecto de Pages, entra en **Custom domains → Set up a domain** y añade `coronapoker.net`. Espera a que el dominio y el certificado estén activos.
7. Para redirigir `www.coronapoker.net` al dominio principal, crea una regla **Bulk Redirects** con origen `www.coronapoker.net`, destino `https://coronapoker.net` y código 301. Activa la coincidencia de subrutas y la conservación de la ruta y de los parámetros de consulta. Para una redirección dedicada, Cloudflare indica crear un registro A de `www` con valor `192.0.2.1` y proxy activado.
8. Comprueba que `/robots.txt` y `/sitemap.xml` responden en el dominio principal. Si configuraste el alias `www`, comprueba que `https://www.coronapoker.net/es/` redirige a `https://coronapoker.net/es/`.

[Integración con Git](https://developers.cloudflare.com/pages/get-started/git-integration/) | [Dominios personalizados](https://developers.cloudflare.com/pages/configuration/custom-domains/) | [Redirección de www](https://developers.cloudflare.com/pages/how-to/www-redirect/)
