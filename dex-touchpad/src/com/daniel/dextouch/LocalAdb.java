package com.daniel.dextouch;

import android.os.Build;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/** ADB identity stored in the app. After one on-phone pairing it can start the touch service. */
public final class LocalAdb extends AbsAdbConnectionManager {
    private final PrivateKey privateKey;
    private final Certificate certificate;

    private LocalAdb(PrivateKey privateKey, Certificate certificate) {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        setApi(Build.VERSION.SDK_INT);
        setHostAddress("127.0.0.1");
        setTimeout(12, TimeUnit.SECONDS);
        setThrowOnUnauthorised(true);
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    public static LocalAdb create(File dir) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        File keyFile = new File(dir, "adbkey.pk8");
        File certFile = new File(dir, "adbkey.cer");
        PrivateKey key;
        Certificate cert;
        if (keyFile.isFile() && certFile.isFile()) {
            byte[] pkcs8 = readAll(keyFile);
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            try (FileInputStream in = new FileInputStream(certFile)) {
                cert = CertificateFactory.getInstance("X.509").generateCertificate(in);
            }
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, new SecureRandom());
            KeyPair pair = generator.generateKeyPair();
            X500Name name = new X500Name("CN=DeX Pad");
            Date notBefore = new Date(System.currentTimeMillis() - 60_000L);
            Date notAfter = new Date(System.currentTimeMillis() + 20L * 365 * 24 * 3600 * 1000);
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name, BigInteger.valueOf(System.currentTimeMillis()), notBefore, notAfter, name, pair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
            X509Certificate created = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
            writeAll(keyFile, pair.getPrivate().getEncoded());
            writeAll(certFile, created.getEncoded());
            key = pair.getPrivate();
            cert = created;
        }
        return new LocalAdb(key, cert);
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @Override
    protected String getDeviceName() {
        return "DeX Pad";
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            return data;
        }
    }

    private static void writeAll(File file, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }
}
