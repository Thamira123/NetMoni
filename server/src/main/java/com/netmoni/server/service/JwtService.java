package com.netmoni.server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.netmoni.server.entity.AccountRole;

@Service
public class JwtService {
	private static final String HEADER=Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
	private final byte[] secret; private final long lifetimeSeconds; private final ObjectMapper mapper;
	public JwtService(@Value("${netmoni.security.jwt-secret}") String secret,
			@Value("${netmoni.security.jwt-lifetime-seconds:3600}") long lifetimeSeconds,ObjectMapper mapper){
		this.secret=secret.getBytes(StandardCharsets.UTF_8);this.lifetimeSeconds=lifetimeSeconds;this.mapper=mapper;
		if(this.secret.length<32)throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
		if(lifetimeSeconds<60)throw new IllegalArgumentException("JWT lifetime must be at least 60 seconds");
	}
	public String issue(String username,AccountRole role){
		try{
			long exp=Instant.now().plusSeconds(lifetimeSeconds).getEpochSecond();
			String payload=Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(Map.of("sub",username,"role",role.name(),"exp",exp)));
			String unsigned=HEADER+"."+payload;
			return unsigned+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(unsigned));
		}catch(Exception e){throw new IllegalStateException("Could not create access token",e);}
	}
	@SuppressWarnings("unchecked")
	public Map<String,Object> validate(String token){
		try{
			String[] parts=token.split("\\.");if(parts.length!=3||!parts[0].equals(HEADER))return null;
			byte[] signature=Base64.getUrlDecoder().decode(parts[2]);if(!MessageDigest.isEqual(signature,sign(parts[0]+"."+parts[1])))return null;
			Map<String,Object> claims=mapper.readValue(Base64.getUrlDecoder().decode(parts[1]),Map.class);
			Object exp=claims.get("exp");if(!(exp instanceof Number n)||n.longValue()<=Instant.now().getEpochSecond())return null;
			if(!(claims.get("sub") instanceof String)||!(claims.get("role") instanceof String))return null;
			return claims;
		}catch(Exception e){return null;}
	}
	public Instant expiresAt(){return Instant.now().plusSeconds(lifetimeSeconds);}
	private byte[] sign(String data)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));}
}
