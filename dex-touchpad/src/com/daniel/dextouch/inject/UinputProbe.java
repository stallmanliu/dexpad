package com.daniel.dextouch.inject;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class UinputProbe {
    private static final int EV_SYN = 0;
    private static final int EV_KEY = 1;
    private static final int EV_REL = 2;
    private static final int SYN_REPORT = 0;
    private static final int REL_X = 0;
    private static final int REL_Y = 1;
    private static final int REL_WHEEL = 8;
    private static final int BTN_LEFT = 0x110;
    private static final int BTN_RIGHT = 0x111;
    private static final int BUS_USB = 0x03;
    private static final int UI_DEV_CREATE = _IO('U', 1);
    private static final int UI_DEV_DESTROY = _IO('U', 2);
    private static final int UI_SET_EVBIT = _IOW('U', 100, 4);
    private static final int UI_SET_KEYBIT = _IOW('U', 101, 4);
    private static final int UI_SET_RELBIT = _IOW('U', 102, 4);

    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        Object im = inputManager();
        Method cursor = im.getClass().getMethod("getCursorPosition", int.class);
        System.out.println("before " + cursor.invoke(im, 7));
        FileDescriptor fd = Os.open("/dev/uinput", OsConstants.O_WRONLY | OsConstants.O_NONBLOCK, 0);
        System.out.println("opened uinput " + fd.valid());
        try {
            ioctl(fd, UI_SET_EVBIT, EV_KEY);
            ioctl(fd, UI_SET_EVBIT, EV_REL);
            ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
            ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
            ioctl(fd, UI_SET_RELBIT, REL_X);
            ioctl(fd, UI_SET_RELBIT, REL_Y);
            ioctl(fd, UI_SET_RELBIT, REL_WHEEL);
            writeUserDev(fd);
            ioctl(fd, UI_DEV_CREATE, 0);
            System.out.println("device created");
            Thread.sleep(300);
            emit(fd, EV_REL, REL_X, 150);
            emit(fd, EV_SYN, SYN_REPORT, 0);
            Thread.sleep(200);
            System.out.println("after +x " + cursor.invoke(im, 7));
            emit(fd, EV_REL, REL_Y, 80);
            emit(fd, EV_SYN, SYN_REPORT, 0);
            Thread.sleep(200);
            System.out.println("after +y " + cursor.invoke(im, 7));
            emit(fd, EV_REL, REL_WHEEL, 1);
            emit(fd, EV_SYN, SYN_REPORT, 0);
            Thread.sleep(100);
            System.out.println("after wheel " + cursor.invoke(im, 7));
        } finally {
            try {
                ioctl(fd, UI_DEV_DESTROY, 0);
            } catch (Throwable ignored) {
            }
            Os.close(fd);
        }
    }

    private static Object inputManager() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Object context = at.getMethod("getSystemContext").invoke(thread);
        java.lang.reflect.Constructor<?> c = Class.forName("android.hardware.input.InputManager")
                .getDeclaredConstructor(android.content.Context.class);
        c.setAccessible(true);
        return c.newInstance(context);
    }

    private static void ioctl(FileDescriptor fd, int cmd, int arg) throws Exception {
        Method m = Os.class.getMethod("ioctlInt", FileDescriptor.class, int.class, int.class);
        try {
            Object r = m.invoke(null, fd, cmd, arg);
            System.out.println(String.format("ioctl 0x%x %d -> %s", cmd, arg, r));
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof ErrnoException) {
                ErrnoException ee = (ErrnoException) c;
                throw new RuntimeException("ioctl 0x" + Integer.toHexString(cmd) + " errno=" + ee.errno + " " + ee.getMessage(), ee);
            }
            throw e;
        }
    }

    private static void writeUserDev(FileDescriptor fd) throws Exception {
        byte[] buf = new byte[80 + 8 + 4 + (64 * 4 * 4)];
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        byte[] name = "DexTouch".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        bb.put(name);
        bb.position(80);
        bb.putShort((short) BUS_USB);
        bb.putShort((short) 0x1234);
        bb.putShort((short) 0x5678);
        bb.putShort((short) 1);
        bb.putInt(0);
        Os.write(fd, buf, 0, buf.length);
        System.out.println("wrote user dev " + buf.length);
    }

    private static void emit(FileDescriptor fd, int type, int code, int value) throws Exception {
        ByteBuffer bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        bb.putLong(0);
        bb.putLong(0);
        bb.putShort((short) type);
        bb.putShort((short) code);
        bb.putInt(value);
        Os.write(fd, bb.array(), 0, 24);
    }

    private static int _IO(char type, int nr) {
        return (type << 8) | nr;
    }

    private static int _IOW(char type, int nr, int size) {
        return (1 << 30) | (size << 16) | (type << 8) | nr;
    }
}
