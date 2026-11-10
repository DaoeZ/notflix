//!DESC Notflix sharpen (3x3 luma unsharp, fixed strength)
//!HOOK MAIN
//!BIND HOOKED

// Fixed, gentle strength (allowed range for the experiment: 0.3 to 0.5).
// This is the only place the intensity lives; notflix_sharpen.glsl is copied to the
// app files dir by NuvioMpvSurfaceView and appended to mpv's glsl-shaders.
#define NF_SHARPEN_STRENGTH 0.40

vec4 hook() {
    vec3 center = HOOKED_texOff(vec2(0.0, 0.0)).rgb;
    vec3 ring =
        HOOKED_texOff(vec2(-1.0, -1.0)).rgb + HOOKED_texOff(vec2(0.0, -1.0)).rgb + HOOKED_texOff(vec2(1.0, -1.0)).rgb +
        HOOKED_texOff(vec2(-1.0,  0.0)).rgb + HOOKED_texOff(vec2(1.0,  0.0)).rgb +
        HOOKED_texOff(vec2(-1.0,  1.0)).rgb + HOOKED_texOff(vec2(0.0,  1.0)).rgb + HOOKED_texOff(vec2(1.0,  1.0)).rgb;

    // 3x3 blur: center weight 4, ring weight 1 (sum 12).
    vec3 blur = (center * 4.0 + ring) / 12.0;

    // Sharpen only the luma detail so colours do not shift.
    float luma = dot(center, vec3(0.2126, 0.7152, 0.0722));
    float lumaBlur = dot(blur, vec3(0.2126, 0.7152, 0.0722));
    vec3 sharpened = center + (luma - lumaBlur) * NF_SHARPEN_STRENGTH;

    return vec4(clamp(sharpened, 0.0, 1.0), HOOKED_texOff(vec2(0.0, 0.0)).a);
}
