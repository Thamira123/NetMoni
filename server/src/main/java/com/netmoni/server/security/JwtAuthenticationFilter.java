package com.netmoni.server.security;
import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.netmoni.server.service.JwtService;
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private final JwtService jwt;
	public JwtAuthenticationFilter(JwtService jwt){this.jwt=jwt;}
	@Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
		String header=request.getHeader("Authorization");
		if(header!=null&&header.startsWith("Bearer ")&&SecurityContextHolder.getContext().getAuthentication()==null){
			var claims=jwt.validate(header.substring(7));
			if(claims!=null){String user=(String)claims.get("sub");String role=(String)claims.get("role");
				if(List.of("ADMIN","OPERATOR","VIEWER").contains(role))SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
		}
		chain.doFilter(request,response);
	}
}
