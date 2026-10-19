#ifdef GL_ES
precision highp float;
#endif

#ifndef SMAA_REPROJECTION
  #define SMAA_REPROJECTION 0
#endif

/**
 * Decodes the velocity buffer: override it when velocity is stored encoded.
 * Velocity is expected in texture coordinates, pointing from the previous frame
 * to the current one.
 */
#ifndef SMAA_DECODE_VELOCITY
  #define SMAA_DECODE_VELOCITY(sample) sample.rg
#endif

#define mad(a, b, c) (a * b + c)

uniform sampler2D uColorTexture;
uniform sampler2D uBlendTexture;

#if SMAA_REPROJECTION
  uniform sampler2D uVelocityTexture;
#endif

uniform vec2 uTexelSize;
uniform float uInteriorEdgeBlendStrength;

varying vec2 vTexCoord0;
varying vec4 vOffset;

/**
 * Conditional move:
 */
void SMAAMovc(bvec2 cond, inout vec2 variable, vec2 value) {
  if (cond.x) variable.x = value.x;
  if (cond.y) variable.y = value.y;
}

void SMAAMovc(bvec4 cond, inout vec4 variable, vec4 value) {
  SMAAMovc(cond.xy, variable.xy, value.xy);
  SMAAMovc(cond.zw, variable.zw, value.zw);
}

float maximumChannelDelta(vec3 first, vec3 second) {
  vec3 delta = abs(first - second);
  return max(max(delta.r, delta.g), delta.b);
}

void main() {
  vec4 original = texture2D(uColorTexture, vTexCoord0);
  vec4 color;

  // Fetch the blending weights for current pixel:
  vec4 a;
  a.x = texture2D(uBlendTexture, vOffset.xy).a; // Right
  a.y = texture2D(uBlendTexture, vOffset.zw).g; // Top
  a.wz = texture2D(uBlendTexture, vTexCoord0).xz; // Bottom / Left

  // Is there any blending weight with a value greater than 0.0?
  if (dot(a, vec4(1.0, 1.0, 1.0, 1.0)) < 1e-5) {
    // Non-edge pixels remain bit-for-bit on the original sampling path.
    color = original;

    #if SMAA_REPROJECTION
      vec2 velocity = SMAA_DECODE_VELOCITY(texture2D(uVelocityTexture, vTexCoord0));

      // Pack velocity into the alpha channel:
      color.a = sqrt(5.0 * length(velocity));
    #endif
  } else {
    bool h = max(a.x, a.z) > max(a.y, a.w); // max(horizontal) > max(vertical)

    // Calculate the blending offsets:
    vec4 blendingOffset = vec4(0.0, a.y, 0.0, a.w);
    vec2 blendingWeight = a.yw;
    SMAAMovc(bvec4(h, h, h, h), blendingOffset, vec4(a.x, 0.0, a.z, 0.0));
    SMAAMovc(bvec2(h, h), blendingWeight, a.xz);
    blendingWeight /= dot(blendingWeight, vec2(1.0, 1.0));

    // Calculate the texture coordinates:
    vec4 blendingCoord = mad(blendingOffset, vec4(uTexelSize, -uTexelSize), vTexCoord0.xyxy);

    // We exploit bilinear filtering to mix current pixel with the chosen
    // neighbor:
    color = blendingWeight.x * texture2D(uColorTexture, blendingCoord.xy); // LinearSampler
    color += blendingWeight.y * texture2D(uColorTexture, blendingCoord.zw); // LinearSampler

    // A thin printed stroke has matching background on both sides of its
    // centre pixel. Resolve those high-confidence strokes completely: this is
    // what removes stair steps from deck-agnostic frames and fine line art.
    // Broad artwork edges have different colours on both sides and retain the
    // conservative resolve, preserving the native texture's apparent detail.
    vec2 edgeNormal = h ? vec2(uTexelSize.x, 0.0)
      : vec2(0.0, uTexelSize.y);
    vec4 outsideNegative = texture2D(uColorTexture,
      vTexCoord0 - 2.0 * edgeNormal);
    vec4 outsidePositive = texture2D(uColorTexture,
      vTexCoord0 + 2.0 * edgeNormal);
    float outsideAgreement = 1.0 - smoothstep(0.025, 0.10,
      maximumChannelDelta(outsideNegative.rgb, outsidePositive.rgb));
    vec3 outsideAverage = 0.5
      * (outsideNegative.rgb + outsidePositive.rgb);
    float strokeContrast = smoothstep(0.08, 0.28,
      maximumChannelDelta(original.rgb, outsideAverage));
    float opaqueStroke = smoothstep(0.98, 1.0,
      min(original.a, min(outsideNegative.a, outsidePositive.a)));
    float thinStroke = outsideAgreement * strokeContrast * opaqueStroke;
    float interiorStrength = mix(uInteriorEdgeBlendStrength, 1.0,
      thinStroke);

    // Alpha-changing silhouette pixels also retain the complete resolve so
    // rounded corners and animated fades remain clean and colour-stable.
    float alphaTransition = smoothstep(0.003, 0.04,
      abs(color.a - original.a));
    float resolveStrength = mix(interiorStrength, 1.0,
      alphaTransition);
    color = mix(original, color, resolveStrength);

    #if SMAA_REPROJECTION
      // Antialias velocity for proper reprojection in a later stage:
      vec2 velocity = blendingWeight.x * SMAA_DECODE_VELOCITY(texture2D(uVelocityTexture, blendingCoord.xy));
      velocity += blendingWeight.y * SMAA_DECODE_VELOCITY(texture2D(uVelocityTexture, blendingCoord.zw));

      // Pack velocity into the alpha channel:
      color.a = sqrt(5.0 * length(velocity));
    #endif
  }

  gl_FragColor = color;
}
