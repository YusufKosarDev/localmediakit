package com.localmediakit.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * TXT resolution via the JDK's built-in JNDI DNS provider — no external
 * dependency. Timeouts are bounded so a slow resolver can never hang the
 * verification job.
 */
@Component
public class JndiDnsResolver implements DnsResolver {

    private final String timeoutMs;
    private final String retries;

    public JndiDnsResolver(
            @Value("${app.domains.dns-timeout-ms:3000}") String timeoutMs,
            @Value("${app.domains.dns-retries:1}") String retries) {
        this.timeoutMs = timeoutMs;
        this.retries = retries;
    }

    @Override
    public List<String> lookupTxt(String host) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns:");
        env.put("com.sun.jndi.dns.timeout.initial", timeoutMs);
        env.put("com.sun.jndi.dns.timeout.retries", retries);

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(host, new String[]{"TXT"});
            Attribute txt = attrs.get("TXT");
            List<String> values = new ArrayList<>();
            if (txt != null) {
                for (int i = 0; i < txt.size(); i++) {
                    values.add(unquote(String.valueOf(txt.get(i))));
                }
            }
            return values;
        } catch (NameNotFoundException e) {
            // No such host / no TXT record: a normal "not verified yet", not an error.
            return List.of();
        } catch (Exception e) {
            throw new DnsLookupException("TXT lookup failed for " + host, e);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception ignored) {
                    // best-effort close
                }
            }
        }
    }

    /** JNDI returns TXT chunks possibly quoted and space-joined; normalize them. */
    private String unquote(String raw) {
        String s = raw.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        // A long TXT is split into quoted 255-byte chunks joined by '" "'.
        return s.replace("\" \"", "");
    }
}
