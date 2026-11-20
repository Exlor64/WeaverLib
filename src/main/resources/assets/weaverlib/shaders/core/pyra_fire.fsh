#version 150
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec2 flameUV;
in vec4 flameColor;
in float vertexDistance;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float turbulence(vec2 p) {
    return noise(p) * 0.57 + noise(p * 2.03 + 12.7) * 0.28 + noise(p * 4.07 - 4.3) * 0.15;
}
void main() {
    float t = GameTime * 1200.0;
    bool aura = flameColor.r > 0.25 && flameColor.r < 0.75;
    bool trail = flameColor.r > 0.75;
    bool core = flameColor.r > 0.06 && flameColor.r < 0.20;
    bool jet = flameColor.g < 0.25;
    bool gildedWing = flameColor.b < 0.75;
    vec2 uv = flameUV;
    float variation = flameColor.g > 0.25 && flameColor.g < 0.999 ? (1.0 - flameColor.g) * 37.0 : 0.0;
    // Eddies advect toward the tips; higher frequencies stretch into fine flame filaments.
    float warp = turbulence(vec2(uv.x * 3.4 + variation, uv.y * 2.0 - t * 1.6 + variation * 0.7));
    vec2 flow = vec2(uv.x * (aura ? 6.0 : 8.0) + (warp - 0.5) * 2.3,
                    uv.y * (jet ? 13.0 : 3.5) - t * (jet ? 6.0 : 2.4)) + vec2(variation * 1.3, variation * 0.77);
    float billow = turbulence(flow);
    float filament = noise(flow * vec2(3.8, 0.42) + vec2(warp * 3.0, -t * 0.8));
    float heat = clamp(0.23 + billow * 0.85 + filament * 0.20 - uv.y * 0.26, 0.0, 1.0);
    float flame = smoothstep(0.39, 0.74, billow + filament * 0.18);
    float tip = 1.0 - smoothstep(0.46 + warp * 0.36, 1.0, uv.y);
    float edge = smoothstep(0.0, 0.09, uv.y) * tip;
    if (aura) {
        // Sparse red wisps hug the animated body; the player's skin remains clearly visible.
        flame = smoothstep(0.58, 0.82, billow + filament * 0.16);
        edge = 0.55 + 0.45 * sin(clamp(uv.y, 0.0, 1.0) * 3.14159);
        heat *= 0.85;
    }
    if (jet) flame = smoothstep(0.46, 0.80, billow + filament * 0.12);
    if (trail) edge *= pow(max(0.0, sin(clamp(uv.x, 0.0, 1.0) * 3.14159)), 0.7);
    vec3 color = mix(vec3(0.55, 0.012, 0.025), vec3(1.0, 0.16, 0.025), smoothstep(0.24, 0.70, heat));
    color = mix(color, vec3(1.0, 0.67, 0.16), smoothstep(0.73, 0.94, heat) * 0.78);
    if (gildedWing) {
        // Optional gold tint for specialized flame surfaces.
        float gold = 0.32 + 0.42 * filament + 0.12 * heat;
        color = mix(color, vec3(1.0, 0.76, 0.25), min(0.86, gold));
    }
    if (core) {
        color = mix(vec3(1.0, 0.32, 0.035), vec3(1.0, 0.95, 0.68),
            smoothstep(0.30, 0.84, billow + filament * 0.28));
        flame = 0.60 + 0.40 * flame;
        edge = 0.72 + 0.28 * tip;
    }
    float alpha = flameColor.a * edge * (aura ? flame : jet ? 0.008 + 0.992 * flame : 0.055 + 0.945 * flame);
    if (alpha < 0.008) discard;
    float fog = smoothstep(FogStart, max(FogStart + 0.001, FogEnd), vertexDistance) * FogColor.a;
    fragColor = vec4(mix(color, FogColor.rgb, fog), alpha) * ColorModulator;
}
