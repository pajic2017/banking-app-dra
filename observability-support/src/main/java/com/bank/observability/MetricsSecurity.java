package com.bank.observability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@AutoConfiguration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class MetricsSecurity {
    @Bean @Order(0) SecurityFilterChain metricsSecurity(HttpSecurity http,@Value("${bank.monitoring.password:}") String secret) throws Exception {
        var encoder=new BCryptPasswordEncoder();var users=new InMemoryUserDetailsManager();
        if(!secret.isBlank())users.createUser(User.withUsername("monitoring").password(encoder.encode(secret)).roles("MONITORING").build());
        var provider=new DaoAuthenticationProvider(users);provider.setPasswordEncoder(encoder);
        return http.securityMatcher("/actuator/prometheus").authenticationProvider(provider)
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).csrf(c->c.disable())
            .authorizeHttpRequests(a->a.anyRequest().hasRole("MONITORING")).httpBasic(b->{ }).build();
    }
}
