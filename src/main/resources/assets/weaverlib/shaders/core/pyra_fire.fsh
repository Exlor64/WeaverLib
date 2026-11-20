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
    vec2 uv = flameUV;
    // Eddies advect toward the tips; higher frequencies stretch into fine flame filaments.
    float warp = turbulence(vec2(uv.x * 3.4, uv.y * 2.0 - t * 1.6));
    vec2 flow = vec2(uv.x * (aura ? 6.0 : 8.0) + (warp - 0.5) * 2.3,
                    uv.y * 3.5 - t * 2.4);
    float billow = turbulence(flow);
    float filament = noise(flow * vec2(2.6, 0.65) + vec2(warp * 3.0, -t * 0.8));
    float heat = clamp(0.23 + billow * 0.85 + filament * 0.20 - uv.y * 0.26, 0.0, 1.0);
    float flame = smoothstep(0.27, 0.66, billow + filament * 0.14);
    float tip = 1.0 - smoothstep(0.46 + warp * 0.36, 1.0, uv.y);
    float edge = smoothstep(0.0, 0.09, uv.y) * tip;
    if (aura) {
        // Sparse red wisps hug the animated body; the player's skin remains clearly visible.
        flame = smoothstep(0.52, 0.78, billow + filament * 0.16);
        edge = 0.55 + 0.45 * sin(clamp(uv.y, 0.0, 1.0) * 3.14159);
        heat *= 0.85;
    }
    if (trail) edge *= pow(max(0.0, sin(clamp(uv.x, 0.0, 1.0) * 3.14159)), 0.7);
    vec3 color = mix(vec3(0.55, 0.012, 0.025), vec3(1.0, 0.16, 0.025), smoothstep(0.24, 0.70, heat));
    color = mix(color, vec3(1.0, 0.67, 0.16), smoothstep(0.73, 0.94, heat) * 0.78);
    float alpha = flameColor.a * edge * (aura ? flame : 0.24 + 0.76 * flame);
    if (alpha < 0.008) discard;
    float fog = smoothstep(FogStart, max(FogStart + 0.001, FogEnd), vertexDistance) * FogColor.a;
    fragColor = vec4(mix(color, FogColor.rgb, fog), alpha) * ColorModulator;
}
