package com.daniel.dextouch.inject;

import android.view.InputEvent;
import android.view.MotionEvent;

import java.lang.reflect.Method;

public final class Probe {
    public static void main(String[] args) throws Exception {
        dump("android.hardware.input.InputManager");
        dump("android.hardware.input.InputManagerGlobal");
        tryInject();
    }

    private static void dump(String name) {
        System.out.println("== " + name);
        try {
            Class<?> c = Class.forName(name);
            for (Method m : c.getDeclaredMethods()) {
                String n = m.getName().toLowerCase();
                if (n.contains("inject") || n.contains("instance") || n.contains("display")
                        || n.contains("pointer") || n.contains("cursor") || n.contains("mouse")
                        || n.contains("dex") || n.contains("relative")) {
                    System.out.println(m.toString());
                }
            }
        } catch (Throwable t) {
            System.out.println("ERR " + t);
        }
    }

    private static void tryInject() {
        System.out.println("== tryInject");
        try {
            Class<?> globalClz = Class.forName("android.hardware.input.InputManagerGlobal");
            Method getInstance = globalClz.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object global = getInstance.invoke(null);
            System.out.println("global=" + global);
            Method inject = null;
            for (Method m : globalClz.getDeclaredMethods()) {
                if (m.getName().equals("injectInputEvent") && m.getParameterTypes().length == 2) {
                    inject = m;
                    break;
                }
            }
            if (inject == null) {
                System.out.println("no 2-arg inject");
                for (Method m : globalClz.getDeclaredMethods()) {
                    if (m.getName().equals("injectInputEvent")) {
                        System.out.println("candidate " + m);
                    }
                }
                return;
            }
            inject.setAccessible(true);
            long now = android.os.SystemClock.uptimeMillis();
            MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
            props[0] = new MotionEvent.PointerProperties();
            props[0].id = 0;
            props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
            coords[0] = new MotionEvent.PointerCoords();
            coords[0].x = 960f;
            coords[0].y = 540f;
            MotionEvent ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE,
                    1, props, coords, 0, 0, 1f, 1f, 0, 0,
                    android.view.InputDevice.SOURCE_MOUSE, 7);
            System.out.println("event display via obtain, source=" + ev.getSource());
            try {
                Method setDisplayId = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
                setDisplayId.setAccessible(true);
                setDisplayId.invoke(ev, 7);
                System.out.println("setDisplayId ok");
            } catch (Throwable t) {
                System.out.println("setDisplayId " + t);
            }
            Object result = inject.invoke(global, ev, 0);
            System.out.println("inject result=" + result);
            ev.recycle();

            Object im = newInputManager();
            System.out.println("im=" + im);
            Method cursor = im.getClass().getMethod("getCursorPosition", int.class);
            System.out.println("cursor0=" + cursor.invoke(im, 7));
            Method sem = im.getClass().getMethod("semInjectInputEvent", InputEvent.class, int.class);
            sem.setAccessible(true);
            MotionEvent semEv = mouse(120f, 80f, 0, MotionEvent.ACTION_HOVER_MOVE, 0);
            Object semResult = sem.invoke(im, semEv, 0);
            System.out.println("semInject result=" + semResult + " cursor=" + cursor.invoke(im, 7));
            semEv.recycle();

            MotionEvent dev14 = mouse(400f, 220f, 14, MotionEvent.ACTION_HOVER_MOVE, 0);
            Object r14 = inject.invoke(global, dev14, 0);
            System.out.println("device14 inject result=" + r14 + " cursor=" + cursor.invoke(im, 7));
            dev14.recycle();

            MotionEvent relEv = mouse(480f, 260f, 14, MotionEvent.ACTION_HOVER_MOVE, 0);
            MotionEvent.PointerCoords rc = new MotionEvent.PointerCoords();
            relEv.getPointerCoords(0, rc);
            rc.setAxisValue(MotionEvent.AXIS_RELATIVE_X, 80f);
            rc.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, -40f);
            relEv.recycle();
            long t = android.os.SystemClock.uptimeMillis();
            MotionEvent.PointerProperties[] rprops = new MotionEvent.PointerProperties[1];
            rprops[0] = new MotionEvent.PointerProperties();
            rprops[0].id = 0;
            rprops[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
            MotionEvent rel2 = MotionEvent.obtain(t, t, MotionEvent.ACTION_HOVER_MOVE, 1, rprops,
                    new MotionEvent.PointerCoords[]{rc}, 0, 0, 1f, 1f, 14, 0,
                    android.view.InputDevice.SOURCE_MOUSE, 7);
            Method setDisplayId = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
            setDisplayId.setAccessible(true);
            setDisplayId.invoke(rel2, 7);
            Object relResult = inject.invoke(global, rel2, 0);
            System.out.println("relative14 result=" + relResult + " cursor=" + cursor.invoke(im, 7));
            rel2.recycle();
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }

    private static Object newInputManager() throws Exception {
        android.os.Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Object context = at.getMethod("getSystemContext").invoke(thread);
        System.out.println("context=" + context);
        Class<?> imClz = Class.forName("android.hardware.input.InputManager");
        java.lang.reflect.Constructor<?> c = imClz.getDeclaredConstructor(android.content.Context.class);
        c.setAccessible(true);
        return c.newInstance(context);
    }

    private static MotionEvent mouse(float x, float y, int deviceId, int action, int buttons) throws Exception {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[1];
        props[0] = new MotionEvent.PointerProperties();
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].x = x;
        coords[0].y = y;
        MotionEvent ev = MotionEvent.obtain(now, now, action, 1, props, coords, 0, buttons, 1f, 1f,
                deviceId, 0, android.view.InputDevice.SOURCE_MOUSE, 7);
        Method setDisplayId = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
        setDisplayId.setAccessible(true);
        setDisplayId.invoke(ev, 7);
        return ev;
    }
}
