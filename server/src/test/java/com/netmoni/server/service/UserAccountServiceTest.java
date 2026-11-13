package com.netmoni.server.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.netmoni.server.dto.auth.*;
import com.netmoni.server.entity.*;
import com.netmoni.server.repository.UserAccountRepository;
@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {
	@Mock UserAccountRepository users; @Mock PasswordEncoder passwords; @Mock JwtService jwt; @InjectMocks UserAccountService service;
	@Test void publicRegistrationCreatesViewerAndReturnsToken(){when(users.existsByUsername("a@example.com")).thenReturn(false);when(passwords.encode("strong-password")).thenReturn("bcrypt-hash");when(users.save(any())).thenAnswer(i->i.getArgument(0));when(jwt.issue("a@example.com",AccountRole.VIEWER)).thenReturn("signed-token");when(jwt.expiresAt()).thenReturn(Instant.parse("2026-12-01T00:00:00Z"));
		AuthResponse response=service.register(new RegisterRequest("A@example.com","strong-password"));assertEquals(AccountRole.VIEWER,response.role());assertEquals("signed-token",response.accessToken());verify(passwords).encode("strong-password");}
	@Test void loginRejectsWrongPassword(){UserAccount account=new UserAccount("a@example.com","hash",AccountRole.VIEWER);when(users.findByUsername("a@example.com")).thenReturn(Optional.of(account));when(passwords.matches("wrong","hash")).thenReturn(false);assertThrows(IllegalArgumentException.class,()->service.login(new LoginRequest("a@example.com","wrong")));}
}
