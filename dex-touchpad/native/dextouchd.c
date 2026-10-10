/* Virtual mouse + keyboard for the DeX cursor. Must run as shell (uhid group). */
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#define PORT 27184

static int g_mouse = -1;
static int g_keys = -1;

static void die(const char *what);

static void daemonize(void) {
    signal(SIGHUP, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);
    pid_t pid = fork();
    if (pid < 0) die("fork");
    if (pid > 0) _exit(0);
    if (setsid() < 0) die("setsid");
    pid = fork();
    if (pid < 0) die("fork2");
    if (pid > 0) _exit(0);
    int devnull = open("/dev/null", O_RDWR);
    int logfd = open("/data/local/tmp/dextouchd.log", O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (devnull >= 0) dup2(devnull, STDIN_FILENO);
    if (logfd >= 0) {
        dup2(logfd, STDOUT_FILENO);
        dup2(logfd, STDERR_FILENO);
    }
    if (devnull > 2) close(devnull);
    if (logfd > 2) close(logfd);
}

static void die(const char *what) {
    perror(what);
    exit(1);
}

static void emit_fd(int fd, int type, int code, int value) {
    struct input_event ie;
    memset(&ie, 0, sizeof(ie));
    ie.type = (unsigned short)type;
    ie.code = (unsigned short)code;
    ie.value = value;
    if (write(fd, &ie, sizeof(ie)) != (ssize_t)sizeof(ie)) {
        perror("write event");
    }
}

static int open_uinput(const char *name, int product) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) die("open /dev/uinput");
    struct uinput_setup setup;
    memset(&setup, 0, sizeof(setup));
    snprintf(setup.name, sizeof(setup.name), "%s", name);
    setup.id.bustype = BUS_USB;
    setup.id.vendor = 0x1234;
    setup.id.product = (unsigned short)product;
    setup.id.version = 1;
    if (ioctl(fd, UI_DEV_SETUP, &setup) < 0) die("UI_DEV_SETUP");
    return fd;
}

static void create_mouse(void) {
    int fd = open_uinput("DeX Pad Mouse", 0x5678);
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) die("EV_KEY");
    if (ioctl(fd, UI_SET_EVBIT, EV_REL) < 0) die("EV_REL");
    if (ioctl(fd, UI_SET_KEYBIT, BTN_LEFT) < 0) die("BTN_LEFT");
    if (ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT) < 0) die("BTN_RIGHT");
    if (ioctl(fd, UI_SET_RELBIT, REL_X) < 0) die("REL_X");
    if (ioctl(fd, UI_SET_RELBIT, REL_Y) < 0) die("REL_Y");
    if (ioctl(fd, UI_SET_RELBIT, REL_WHEEL) < 0) die("REL_WHEEL");
    if (ioctl(fd, UI_SET_RELBIT, REL_HWHEEL) < 0) die("REL_HWHEEL");
    if (ioctl(fd, UI_DEV_CREATE) < 0) die("UI_DEV_CREATE");
    g_mouse = fd;
}

static void create_keyboard(void) {
    int fd = open_uinput("DeX Pad Keyboard", 0x5679);
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) die("EV_KEY");
    if (ioctl(fd, UI_SET_EVBIT, EV_MSC) < 0) die("EV_MSC");
    if (ioctl(fd, UI_SET_MSCBIT, MSC_SCAN) < 0) die("MSC_SCAN");
    for (int code = 1; code <= 248; code++) {
        ioctl(fd, UI_SET_KEYBIT, code);
    }
    if (ioctl(fd, UI_DEV_CREATE) < 0) die("UI_DEV_CREATE");
    g_keys = fd;
}

static void click(int button) {
    emit_fd(g_mouse, EV_KEY, button, 1);
    emit_fd(g_mouse, EV_SYN, SYN_REPORT, 0);
    emit_fd(g_mouse, EV_KEY, button, 0);
    emit_fd(g_mouse, EV_SYN, SYN_REPORT, 0);
}

static void key_event(int code, int down) {
    /* Generic.kl looks up the MSC_SCAN value. Without it every key is KEYCODE_UNKNOWN. */
    emit_fd(g_keys, EV_MSC, MSC_SCAN, code);
    emit_fd(g_keys, EV_KEY, code, down ? 1 : 0);
    emit_fd(g_keys, EV_SYN, SYN_REPORT, 0);
}

static void handle(char *line) {
    int a = 0, b = 0;
    if (line[0] == '\0') return;
    if (strcmp(line, "L") == 0) {
        click(BTN_LEFT);
    } else if (strcmp(line, "R") == 0) {
        click(BTN_RIGHT);
    } else if (strcmp(line, "PD L") == 0) {
        emit_fd(g_mouse, EV_KEY, BTN_LEFT, 1);
        emit_fd(g_mouse, EV_SYN, SYN_REPORT, 0);
    } else if (strcmp(line, "PU L") == 0) {
        emit_fd(g_mouse, EV_KEY, BTN_LEFT, 0);
        emit_fd(g_mouse, EV_SYN, SYN_REPORT, 0);
    } else if (strcmp(line, "PD R") == 0) {
        emit_fd(g_mouse, EV_KEY, BTN_RIGHT, 1);
        emit_fd(g_mouse, EV_SYN, SYN_REPORT, 0);
    } else if (strcmp(line, "PU R") == 0) {
        emit_fd(g_mouse, EV_KEY, BTN_RIGHT, 0);
        emit_fd(g_mouse, EV_SYN, SYN_REPORT, 0);
    } else if (sscanf(line, "M %d %d", &a, &b) == 2) {
        if (a > 2048) a = 2048;
        if (a < -2048) a = -2048;
        if (b > 2048) b = 2048;
        if (b < -2048) b = -2048;
        if (a) emit_fd(g_mouse, EV_REL, REL_X, a);
        if (b) emit_fd(g_mouse, EV_REL, REL_Y, b);
        if (a || b) emit_fd(g_mouse, EV_SYN, SYN_REPORT, 0);
    } else if (sscanf(line, "W %d %d", &a, &b) == 2) {
        if (a) emit_fd(g_mouse, EV_REL, REL_WHEEL, a);
        if (b) emit_fd(g_mouse, EV_REL, REL_HWHEEL, b);
        if (a || b) emit_fd(g_mouse, EV_SYN, SYN_REPORT, 0);
    } else if (sscanf(line, "K %d %d", &a, &b) == 2) {
        if (a > 0 && a < 248) key_event(a, b);
    } else if (strcmp(line, "PING") == 0) {
        /* keepalive */
    } else {
        fprintf(stderr, "unknown: %s\n", line);
    }
}

static int client_send(const char *msg) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) die("socket");
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(PORT);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) die("connect");
    size_t len = strlen(msg);
    if (write(fd, msg, len) != (ssize_t)len) die("write");
    if (msg[len - 1] != '\n') {
        if (write(fd, "\n", 1) != 1) die("write nl");
    }
    close(fd);
    return 0;
}

static void serve(void) {
    int srv = socket(AF_INET, SOCK_STREAM, 0);
    if (srv < 0) die("socket");
    int one = 1;
    setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(PORT);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(srv, (struct sockaddr *)&addr, sizeof(addr)) < 0) die("bind");
    if (listen(srv, 1) < 0) die("listen");
    fprintf(stderr, "dextouchd listening 127.0.0.1:%d\n", PORT);
    fflush(stderr);

    char buf[2048];
    for (;;) {
        int client = accept(srv, NULL, NULL);
        if (client < 0) {
            if (errno == EINTR) continue;
            die("accept");
        }
        setsockopt(client, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));
        fprintf(stderr, "client connected\n");
        fflush(stderr);
        int n = 0;
        for (;;) {
            int r = read(client, buf + n, sizeof(buf) - 1 - n);
            if (r <= 0) break;
            n += r;
            buf[n] = '\0';
            char *start = buf;
            char *nl;
            while ((nl = strchr(start, '\n')) != NULL) {
                *nl = '\0';
                if (nl > start && nl[-1] == '\r') nl[-1] = '\0';
                handle(start);
                start = nl + 1;
            }
            int remain = (int)((buf + n) - start);
            memmove(buf, start, (size_t)remain);
            n = remain;
            if (n > 1800) n = 0;
        }
        close(client);
        fprintf(stderr, "client disconnected\n");
        fflush(stderr);
    }
}

int main(int argc, char **argv) {
    if (argc >= 3 && strcmp(argv[1], "send") == 0) {
        return client_send(argv[2]);
    }
    daemonize();
    create_mouse();
    create_keyboard();
    usleep(200000);
    fprintf(stderr, "uinput ready mouse=%d keys=%d\n", g_mouse, g_keys);
    fflush(stderr);
    serve();
    return 0;
}
