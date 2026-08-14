# Auditoría integral de CoronaPoker 23.44

Base: `204481c6c` (`chore(release): CoronaPoker 23.44`).
Rama de auditoria: `audit/23.44-integral` (rama temporal conservada para
trazabilidad; worktree ya retirado); resultados integrados en
`release/23.45-game-fixes`.

## Estado de ejecución

Estos informes forman parte del handoff de 23.45; la base funcional sigue siendo exactamente 23.44.

- Fase 0: cerrada; Fase A/P0 en curso, con dos hallazgos funcionales y sus
  familias abiertas.
- Java: `25.0.1`; Maven de NetBeans: `3.9.11`.
- Código de 23.44: aislado en este worktree, sin cambios de 23.45.
- Hallazgos funcionales: `P0-001` aceptado en `fc82a14de` y ampliado en
  `30971c327`; quedan abiertas sus pruebas de familia (RIT, Rabbit/IWTSTH,
  recuperación, empate y multi-side-pot). `P0-002` aceptado en `fc82a14de`
  para validar/retransmitir rebuys; queda abierto su smoke host/cliente y la
  carrera con persistencia.
- El `mvn install` de la raíz compila el código, pero el plugin
  `build-helper:remove-project-artifact` no puede borrar el artefacto existente
  en el repositorio local por permisos del sandbox.
- El reactor `tools/reactor/pom.xml` no llega a compilar QA: las pruebas no
  reciben las clases de la aplicación (`Helpers`, `GameFrame`, `Crupier`, etc.).
  Se registra como bloqueo de infraestructura del reactor, no como bug del juego.
- La ejecución independiente de `tools/qa` sí compila 135 fuentes de test. En
  este runner falla la preparación de identidad/ACL: 3 assertions de
  `IdentityKeypairAclSmoke` y 6 errores de `ReceiptSignatureTest`. La ACL de
  Windows se restringe a `PEPINAZO\\Antonio`, mientras el proceso sandbox no es
  ese usuario; se debe repetir en NetBeans bajo la cuenta real antes de abrir un
  defecto funcional.

## Evidencia inicial

- Commit base comprobado: `204481c6c`.
- La raíz del proyecto es el artefacto del juego; la suite vive en `tools/qa`.
- `tools/reactor/pom.xml` es el reactor opt-in y define las lanes fast/slow/all
  mediante los perfiles de `tools/qa/pom.xml`.
- La ejecución reproducible de un test dirigido usa el Maven de NetBeans y
  `-Dmaven.repo.local=C:\\Users\\Antonio\\.m2\\repository`; para no tocar la
  identidad real se debe proporcionar un `user.home` de QA, aunque la ACL de
  Windows sigue requiriendo que el proceso sea el usuario propietario.

## Próxima acción

Completar el mapa de estados y llamadas de `Crupier`, `HandPot`, `Player`,
`GameFrame`, `Participant`, `WaitingRoomFrame`, `NetClient` y SQLite; después
cerrar las matrices de familia de `P0-001` y `P0-002` antes de abrir el
siguiente hallazgo.

## Última evidencia

- Fast lane sobre el artefacto 23.45 (POM auxiliar temporal, eliminada al
  terminar): 734 tests en 128 clases; 725 pasan. Los 9 restantes son los
  bloqueos de identidad/ACL de Windows ya aislados (3 assertions de
  `IdentityKeypairAclSmoke` y 6 errores de `ReceiptSignatureTest`), no fallos
  funcionales del juego.
- Rojo: `HandPotCharacterizationTest` contra el artefacto 23.44: 15 tests, 1
  failure (`expected side-pot count 1, got 0`).
- Rojo de la ampliación: contra el artefacto 23.44 faltaban los helpers de
  conservación entre calles; se registró como regresión de compilación antes
  del segundo parche.
- Verde: `HandPotCharacterizationTest` (incluida la prueba de permanencia entre
  calles) y vecinos de pot math, RIT, Rabbit e IWTSTH pasan contra las clases
  recompiladas de `30971c327`.
- Rojo aislado de rebuys: una copia limpia de 23.44 no compila
  `RebuyAmountValidationTest` porque no existen `normalizeRequestedRebuy` ni
  `canonicalRemoteRebuyAmount`.
- Verde de rebuys: 3 pruebas dirigidas y 48 pruebas vecinas pasan contra el jar
  recompilado de `fc82a14de`.
- Lane rápida completa sobre el jar auditado: 631 tests en 86 clases; 622 pasan
  y 9 quedan bloqueados por la ACL/identidad de Windows (3 assertions de
  `IdentityKeypairAclSmoke`, 6 errores de `ReceiptSignatureTest`). El resto de
  dominio, protocolo, crypto y smoke pasó; repetir esos 9 bajo NetBeans con la
  cuenta `PEPINAZO\\Antonio`.
- No-cambio registrado: `NO-CHANGE-exited-allin-reveal.md` deja anotada la
  carrera `ALLIN -> EXIT -> POTCARDS`; no se relaja la verificación SRA sin un
  smoke host/cliente determinista.
- El POM auxiliar de esa comprobación fue temporal y se eliminó; el bloqueo del
  reactor por rutas con espacios y el repositorio Maven protegido queda como
  infraestructura pendiente, no como cambio funcional.
- Si una prueba de la familia no puede ejecutarse en este entorno, registrar
  ficha de no-cambio con el escenario, riesgo y test/instrumentación pendiente.

## Evidencia de lanes lenta y de bots (2026-08-14)

- Se corrigieron los perfiles `slow-bot`, `slow-crypto` y
  `slow-integration`: antes heredaban `excludedGroups=slow` y podían terminar
  con `BUILD SUCCESS` y cero tests. La corrección está en
  `tools/qa/pom.xml` y se reprodujo con una prueba mínima de bot.
- `slow-crypto`: 88/88 verdes; `slow-integration`: 3/3 verdes.
- `slow-bot`: 25 tests completos (13 clases, volumen 200x50) verdes. Las
  cuatro clases restantes se ejecutaron a 40x25: dos verdes y dos con media
  positiva pero `t<2` por potencia muestral; una repetición completa requiere
  NetBeans/usuario real y supera el timeout del sandbox. El detalle queda en
  [`TEST-LANES-23.45.md`](TEST-LANES-23.45.md).

- P0-001: `HandPotCharacterizationTest` ampliado a 17 tests con dos all-ins
  desconectados en capas distintas. La mutación 23.44 produjo 2 rojos y la
  lógica 23.45 quedó verde; commit TDD `d921b1d52`.
- P0-002: `RebuyAmountValidationTest` ampliado a 4 tests (overflow, espacios,
  `null` y headroom negativo); 4/4 verdes contra el JAR 23.45, commit TDD
  `84490d8a8`. No se justifica otro cambio de producción sin smoke host/cliente.
- Las dos familias P0 siguen abiertas únicamente en sus cruces reales de
  host/cliente, SQLite, retransmisión y recuperación indicados en sus fichas;
  no se relaja el veredicto criptográfico ni se convierte la lane estadística
  de bots en una prueba funcional.
