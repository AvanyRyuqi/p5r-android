#version 450
// Host pixel -> the picture: `rot` turns host uv into upright uv (the swapchain's preTransform undone), `box` is where
// the picture sits in the upright target (uv, fit with bars), `crop` the part of the texture shown. Alpha is 1.
// `post.yz` optionally applies zero-cost vibrance and subtle S-curve contrast grading.
layout(set = 0, binding = 0) uniform sampler2D src;
layout(push_constant) uniform P {
    vec4 rot;   // mat2 columns (x, y) (z, w)
    vec4 off;   // xy: rot offset; z: sharpness; w: target width / height (upright)
    vec4 box;   // x0 y0 w h
    vec4 crop;  // u0 v0 du dv
    vec4 post;  // x: CAS strength, y: vibrance, z: contrast, w: reserved
} p;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 color;

vec3 apply_color(vec3 c, float vib, float con) {
    if (vib > 0.001) {
        float mx = max(c.r, max(c.g, c.b));
        float mn = min(c.r, min(c.g, c.b));
        float sat = mx - mn;
        float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c = mix(vec3(luma), c, 1.0 + vib * (1.0 - sat * 0.65));
    }
    if (con > 0.001) {
        vec3 s = c * c * (3.0 - 2.0 * c);
        c = mix(c, s, con);
    }
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec2 up = mat2(p.rot.xy, p.rot.zw) * uv + p.off.xy;
    vec2 b = (up - p.box.xy) / p.box.zw;
    vec3 c = vec3(0.0);
    if (all(greaterThanEqual(b, vec2(0.0))) && all(lessThanEqual(b, vec2(1.0)))) {
        c = texture(src, p.crop.xy + b * p.crop.zw).rgb;
        if (p.post.y > 0.001 || p.post.z > 0.001)
            c = apply_color(c, p.post.y, p.post.z);
    }
    color = vec4(c, 1.0);
}
