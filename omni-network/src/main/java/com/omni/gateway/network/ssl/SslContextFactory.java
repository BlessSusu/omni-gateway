package com.omni.gateway.network.ssl;

import com.omni.gateway.core.config.TlsConfig;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

public class SslContextFactory {

    private static final Logger log = LoggerFactory.getLogger(SslContextFactory.class);

    private final AtomicReference<SslContext> current = new AtomicReference<>();
    private final AtomicReference<SslContext> next = new AtomicReference<>();
    private volatile TlsConfig lastConfig;

    public synchronized void reload(TlsConfig config) throws Exception {
        SslContext built = build(config);
        if (current.get() == null) {
            current.set(built);
        } else {
            next.set(built);
        }
        lastConfig = config;
        log.info("TLS context prepared enabled={} mtls={}", config.isEnabled(), config.isMtlsEnabled());
    }

    public SslContext contextForNewConnection() {
        SslContext n = next.getAndSet(null);
        if (n != null) {
            current.set(n);
        }
        SslContext c = current.get();
        if (c == null) {
            throw new IllegalStateException("TLS not initialized");
        }
        return c;
    }

    public boolean isReady() {
        return current.get() != null;
    }

    private SslContext build(TlsConfig config) throws Exception {
        if (!config.isEnabled()) {
            return null;
        }
        X509Certificate cert = loadCert(config.getCertPath());
        PrivateKey key = loadPrivateKey(config.getKeyPath());
        SslContextBuilder builder = SslContextBuilder.forServer(key, cert);
        if (config.isMtlsEnabled()) {
            X509Certificate trust = loadCert(config.getTrustCertPath());
            builder.trustManager(trust).clientAuth(ClientAuth.REQUIRE);
        }
        return builder.build();
    }

    private static X509Certificate loadCert(String path) throws Exception {
        try (InputStream in = open(path)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    private static PrivateKey loadPrivateKey(String path) throws Exception {
        String pem = new String(open(path).readAllBytes())
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static InputStream open(String path) throws Exception {
        if (path.startsWith("classpath:")) {
            String resource = path.substring("classpath:".length());
            InputStream in = SslContextFactory.class.getClassLoader().getResourceAsStream(resource);
            if (in == null) {
                throw new IllegalArgumentException("Classpath resource not found: " + resource);
            }
            return in;
        }
        return Files.newInputStream(Path.of(path));
    }
}
