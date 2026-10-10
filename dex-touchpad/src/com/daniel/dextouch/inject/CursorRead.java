package com.daniel.dextouch.inject;

public final class CursorRead {
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Object context = at.getMethod("getSystemContext").invoke(thread);
        java.lang.reflect.Constructor<?> c = Class.forName("android.hardware.input.InputManager")
                .getDeclaredConstructor(android.content.Context.class);
        c.setAccessible(true);
        Object im = c.newInstance(context);
        Object pos = im.getClass().getMethod("getCursorPosition", int.class).invoke(im, 7);
        System.out.println(pos);
    }
}
