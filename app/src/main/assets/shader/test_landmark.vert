#version 300 es
layout(location = 0) in vec2 in_position;

uniform mat4 texTransform;

void main() {
    gl_Position = vec4(in_position*2.0-1.0, 0.0, 1.0);
    gl_PointSize = 2.0;
}