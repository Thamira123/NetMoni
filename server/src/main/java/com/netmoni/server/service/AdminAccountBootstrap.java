package com.netmoni.server.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.netmoni.server.entity.AccountRole;
import com.netmoni.server.entity.UserAccount;
import com.netmoni.server.repository.UserAccountRepository;
@Component
public class AdminAccountBootstrap implements ApplicationRunner {
	private final UserAccountRepository users; private final PasswordEncoder encoder; private final boolean enabled; private final String username,password;
	public AdminAccountBootstrap(UserAccountRepository users,PasswordEncoder encoder,
			@Value("${netmoni.security.bootstrap-admin.enabled:false}") boolean enabled,
			@Value("${netmoni.security.bootstrap-admin.username:}") String username,
			@Value("${netmoni.security.bootstrap-admin.password:}") String password){this.users=users;this.encoder=encoder;this.enabled=enabled;this.username=username;this.password=password;}
	@Override @Transactional public void run(ApplicationArguments args){if(!enabled)return;
		if(username.isBlank()||password.length()<10)throw new IllegalStateException("Bootstrap admin requires username and a password of at least 10 characters");
		String normalized=username.trim().toLowerCase();
		if(!users.existsByUsername(normalized))users.save(new UserAccount(normalized,encoder.encode(password),AccountRole.ADMIN));
	}
}
