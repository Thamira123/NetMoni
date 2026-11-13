package com.netmoni.agent.monitoring.snmp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmoni.agent.model.MonitoringResult;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthHMAC192SHA256;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public final class SnmpMonitor {
    private static final String[] OIDS = {"1.3.6.1.2.1.1.1.0", "1.3.6.1.2.1.1.3.0", "1.3.6.1.2.1.1.5.0"};
    private final String version;
    private final int port;
    private final String community;
    private final String securityName;
    private final String authProtocol;
    private final String authPassphrase;
    private final String privacyProtocol;
    private final String privacyPassphrase;
    private final int timeoutMs;
    private final ObjectMapper objectMapper;

    public SnmpMonitor(String version, int port, String community, int timeoutMs, ObjectMapper objectMapper) {
        this(version, port, community, "", "SHA256", "", "AES128", "", timeoutMs, objectMapper);
    }

    public SnmpMonitor(String version, int port, String community, String securityName,
                       String authProtocol, String authPassphrase, String privacyProtocol,
                       String privacyPassphrase, int timeoutMs, ObjectMapper objectMapper) {
        this.version = version;
        this.port = port;
        this.community = community;
        this.securityName = securityName;
        this.authProtocol = authProtocol;
        this.authPassphrase = authPassphrase;
        this.privacyProtocol = privacyProtocol;
        this.privacyPassphrase = privacyPassphrase;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
    }

    public MonitoringResult check(String ipAddress) {
        Instant checkedAt = Instant.now();
        if (!"3".equals(version) && community.isBlank()) {
            return failure(checkedAt, "SNMP community is not configured");
        }
        try (Snmp snmp = new Snmp(new DefaultUdpTransportMapping())) {
            snmp.listen();
            org.snmp4j.Target<UdpAddress> target;
            if ("3".equals(version)) {
                SecurityProtocols.getInstance().addDefaultProtocols();
                OID authOid = "SHA".equals(authProtocol) ? AuthSHA.ID : AuthHMAC192SHA256.ID;
                OID privacyOid = PrivAES128.ID;
                OctetString user = new OctetString(securityName);
                snmp.getUSM().addUser(user,
                        new UsmUser(user, authOid, new OctetString(authPassphrase),
                                privacyOid, new OctetString(privacyPassphrase)));
                UserTarget<UdpAddress> userTarget = new UserTarget<>();
                userTarget.setSecurityName(user);
                userTarget.setSecurityLevel(SecurityLevel.AUTH_PRIV);
                userTarget.setVersion(SnmpConstants.version3);
                userTarget.setAddress(new UdpAddress(ipAddress + "/" + port));
                userTarget.setRetries(0);
                userTarget.setTimeout(timeoutMs);
                target = userTarget;
            } else {
                CommunityTarget<UdpAddress> communityTarget = new CommunityTarget<>();
                communityTarget.setCommunity(new OctetString(community));
                communityTarget.setVersion("1".equals(version) ? SnmpConstants.version1 : SnmpConstants.version2c);
                communityTarget.setAddress(new UdpAddress(ipAddress + "/" + port));
                communityTarget.setRetries(0);
                communityTarget.setTimeout(timeoutMs);
                target = communityTarget;
            }

            PDU request = new PDU();
            for (String oid : OIDS) {
                request.add(new VariableBinding(new OID(oid)));
            }
            request.setType(PDU.GET);
            long started = System.nanoTime();
            ResponseEvent<UdpAddress> event = snmp.send(request, target);
            PDU response = event == null ? null : event.getResponse();
            if (response == null || response.getErrorStatus() != PDU.noError) {
                return failure(checkedAt, response == null ? "SNMP request timed out"
                        : response.getErrorStatusText());
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (VariableBinding binding : response.getAll()) {
                values.put(binding.getOid().toString(), binding.getVariable().toString());
            }
            return new MonitoringResult("SNMP", (System.nanoTime() - started) / 1_000_000,
                    true, null, null, null, objectMapper.writeValueAsString(values), checkedAt, null);
        } catch (IOException | RuntimeException exception) {
            return failure(checkedAt, exception.getMessage());
        }
    }

    private static MonitoringResult failure(Instant checkedAt, String error) {
        return new MonitoringResult("SNMP", null, false, null, null, null, null, checkedAt, error);
    }
}
