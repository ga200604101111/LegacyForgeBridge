package dev.longyu.legacyforgebridge.render;

/**
 * A converted mod's compiled render program. No Minecraft names appear in this ABI so generated
 * classes can be loaded in production without accidentally using development mapping names.
 */
public interface LegacyEquipmentProgram {
    void render(Sink sink, float[] inputs, boolean crouching);

    interface Sink {
        void push();
        void pop();
        void translate(float x, float y, float z);
        void scale(float x, float y, float z);
        void rotate(float degrees, float x, float y, float z);
        void draw(String model, String texture, boolean lighting, boolean cull, boolean translucent);
    }
}
