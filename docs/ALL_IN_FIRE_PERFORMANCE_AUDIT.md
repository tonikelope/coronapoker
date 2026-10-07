# ALL-IN fire performance audit

Date: 2026-10-07  
Scope: libGDX persistent ALL-IN fire and structurally similar winner effect
Status: safe CPU/GC optimisations implemented; rendering parameters unchanged

## Executive conclusion

The effect is already cheap while inactive: `drawAllInSeatFlames()` reuses its
seat list and returns before opening any render pass when no visible seat owns
an ALL-IN action.

The implementation on this branch removes transient flame-bound allocations,
reuses each active seat's opacity/avatar position across every pass and caches
the immutable per-particle constants used by the embers. The same conservative
pattern is applied to the winner glow: its active-seat list is reused and its
immutable particle constants are calculated once. Counts, geometry segments,
positions, equations, colours, alpha, seeds, layers, blend modes, shader source
and timing are unchanged.

While active, the dominant cost is the fragment shader rather than the Java
particle calculations. Each ALL-IN seat renders three procedural flame layers
twice: first as additive bloom and then as the normal-alpha flame. That is six
procedural draws per seat and frame, followed by a separate `ShapeRenderer`
pass for the embers.

At a 2560x1440 16:9 framebuffer, the six layer rectangles cover approximately
263,964 fragments per fully visible ALL-IN seat and frame. At 240 FPS this is
about 63.35 million fragment-shader invocations per second per seat, before
accounting for clipping at screen edges. Multiple simultaneous ALL-IN seats
scale this cost almost linearly.

The shader source performs approximately 68 value-noise evaluations per
fragment. In source-level terms those expand to roughly 290 sine evaluations,
in addition to interpolation, powers, smooth steps and blending. This is not a
hardware instruction count--the driver and GPU can optimise it--but it clearly
identifies an ALU-heavy fragment path.

## Relevant implementation

- Shader: `CoronaPokerGdxTable.ALL_IN_FIRE_FRAGMENT_SHADER`.
- Render path: `CoronaPokerGdxTable.drawAllInSeatFlames()`.
- Geometry: `CoronaPokerGdxTable.allInFireLayerBounds()`.
- Feature gate: `CoronaPokerGdxTable.liveAllInFireEnabled()`.
- User-facing impact classification: `GdxSettingsContract.performanceImpact()`
  already marks `animacion_fuego_allin` as `HIGH`.

Resources are created once during table construction and disposed with the
table. There is no per-frame texture loading or shader compilation.

## Recommended optimisation order

The following larger GPU changes remain analysis-only. They can offer a much
larger gain, but an intermediate render target or a rewritten vertex format
cannot honestly be promised to produce byte-identical pixels on every GPU and
driver. They should therefore remain behind an A/B development path until
capture comparison and GPU-time measurements approve them.

### 1. Evaluate the procedural field once and compose it twice

Render the expensive procedural data for every active seat/layer into a
persistent off-screen atlas at the current physical framebuffer density. Pack
the intermediate values needed by the existing final colour equations--raw
flame, holes, heat and smoke noise--into its channels. Then reproduce the
existing normal and expanded bloom appearances with two inexpensive composite
passes.

The atlas must be persistent and resized only when framebuffer density or its
required capacity changes. It must still be regenerated every rendered frame,
so 240 Hz temporal motion remains intact. The three layers, independent seeds,
smoke, holes, colours, sizes and blend order can all be retained.

Expected result: approximately half of the expensive procedural fragment work,
traded for inexpensive texture composition and framebuffer bandwidth. This is
the highest-return candidate that does not deliberately reduce spatial or
temporal quality. It still requires an A/B GPU-time measurement because the
exact gain depends on the target GPU and driver.

### 2. Batch all flame geometry per pass

The current uniforms change for every layer, forcing a flush before every
quad. A dedicated mesh can carry seed, layer time multiplier, alpha and seat
parameters as per-vertex attributes. This permits one draw for all bloom layers
and one draw for all normal layers while keeping the same shader equations.

Expected result: reduce `6 * active ALL-IN seats` flame draw calls to two. This
primarily lowers CPU and driver overhead and becomes more valuable with several
simultaneous ALL-IN players.

### 3. Batch embers as pre-rasterised sprites

Each active seat currently produces 25 embers, with two filled circles per
ember and 7/8 segments respectively. A small antialiased ember/wake texture can
reproduce the same apparent shape using batched quads and additive blending.

Expected result: much less transient geometry and no separate circle-heavy
`ShapeRenderer` workload. This is secondary to the fragment shader.

### 4. Remove small per-frame overheads (implemented)

- Reuse layer bounds instead of allocating six `Rectangle` objects per active
  seat and frame.
- Cache the seat avatar X coordinate and presence once per active seat in the
  fire pass.
- Precompute immutable ember and winner-particle constants.
- Reuse the winner-seat collection instead of resolving winners twice.
- If profiling demonstrates value, maintain the active fire-seat set at visual
  event boundaries rather than scanning the ten seats every frame.

These are safe clean-ups. They reduce CPU work and garbage-collection pressure,
but do not address the dominant procedural fragment-shader cost.

## Options deliberately not recommended first

- Reducing FBM octaves or plume count: changes fine detail.
- Updating the fire at 60/120 Hz while the table renders at 240 Hz: reduces
  temporal fidelity.
- Replacing the procedural effect with an offline sprite loop: introduces a
  visible repetition period and extra asset memory.
- Replacing the hash/noise implementation before measuring: potentially a
  large gain, but changes the exact turbulence character and is less visually
  conservative than eliminating the duplicated procedural pass.

## Validation required before accepting an implementation

1. Keep the current implementation behind an A/B development switch.
2. Compare GPU frame time with zero, one, three and the maximum practical
   number of simultaneous ALL-IN seats at 1080p, 1440p and 4K.
3. Test at both 120 and 240 FPS with VSync disabled and a fixed cap.
4. Capture identical frames from both paths and compare layer placement,
   bloom, smoke, alpha and overlap order around the avatar/HUD.
5. Reject the optimisation if frame-time variance increases or if the atlas
   introduces resampling softness, edge seams or allocation spikes.

The focused `GdxTableViewStateTest` suite compiles the renderer and passes all
202 tests. Automated tests do not constitute visual or GPU-time certification;
the implemented changes are deliberately limited to invariant caching and
storage reuse so that the render inputs remain the same.
