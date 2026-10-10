#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static void emit(int fd, int type, int code, int value) {
    struct input_event ie;
    memset(&ie, 0, sizeof(ie));
    ie.type = type;
    ie.code = code;
    ie.value = value;
    if (write(fd, &ie, sizeof(ie)) != (ssize_t)sizeof(ie)) {
        perror("write event");
    }
}

int main(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        perror("open");
        return 1;
    }
    printf("fd=%d sizeof(input_event)=%zu sizeof(uinput_user_dev)=%zu\n",
           fd, sizeof(struct input_event), sizeof(struct uinput_user_dev));
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) perror("EV_KEY");
    if (ioctl(fd, UI_SET_EVBIT, EV_REL) < 0) perror("EV_REL");
    if (ioctl(fd, UI_SET_KEYBIT, BTN_LEFT) < 0) perror("BTN_LEFT");
    if (ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT) < 0) perror("BTN_RIGHT");
    if (ioctl(fd, UI_SET_RELBIT, REL_X) < 0) perror("REL_X");
    if (ioctl(fd, UI_SET_RELBIT, REL_Y) < 0) perror("REL_Y");
    if (ioctl(fd, UI_SET_RELBIT, REL_WHEEL) < 0) perror("REL_WHEEL");

    struct uinput_setup setup;
    memset(&setup, 0, sizeof(setup));
    snprintf(setup.name, sizeof(setup.name), "DexTouch");
    setup.id.bustype = BUS_USB;
    setup.id.vendor = 0x1234;
    setup.id.product = 0x5678;
    setup.id.version = 1;
    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0) {
        perror("UI_DEV_SETUP");
        struct uinput_user_dev dev;
        memset(&dev, 0, sizeof(dev));
        snprintf(dev.name, sizeof(dev.name), "DexTouch");
        dev.id.bustype = BUS_USB;
        dev.id.vendor = 0x1234;
        dev.id.product = 0x5678;
        if (write(fd, &dev, sizeof(dev)) != (ssize_t)sizeof(dev)) perror("write dev");
    }
    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        perror("UI_DEV_CREATE");
        close(fd);
        return 1;
    }
    printf("created\n");
    usleep(200000);
    emit(fd, EV_REL, REL_X, 180);
    emit(fd, EV_SYN, SYN_REPORT, 0);
    usleep(150000);
    emit(fd, EV_REL, REL_Y, 90);
    emit(fd, EV_SYN, SYN_REPORT, 0);
    usleep(150000);
    printf("moved\n");
    fflush(stdout);
    sleep(4);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
