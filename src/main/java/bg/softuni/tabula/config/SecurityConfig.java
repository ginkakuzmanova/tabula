package bg.softuni.tabula.config;

import bg.softuni.tabula.user.OAuth2UserAuthSuccessHandler;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@AllArgsConstructor
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

  private final UserDetailsService userDetailsService;

  private final OAuth2UserAuthSuccessHandler oAuth2UserAuthSuccessHandler;

  private final PasswordEncoder passwordEncoder;

  @Override
  protected void configure(HttpSecurity http) throws Exception {

    // for the ant pattern matcher syntax, please check:
    // https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html

    http
        .authorizeRequests()
        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
        .antMatchers("/login**", "/login-error**", "/registration").permitAll()
        .antMatchers("/**")
        .authenticated().
        and()
          .formLogin()
          .loginPage("/login")
          .loginProcessingUrl("/login/authenticate")
          .failureForwardUrl("/login-error")
          .successForwardUrl("/home")
        .and()
          .logout()
          .logoutUrl("/logout")
          .logoutSuccessUrl("/login")
          .invalidateHttpSession(true)
          .deleteCookies("JSESSIONID").
        and().
          oauth2Login().
          loginPage("/login").
          successHandler(oAuth2UserAuthSuccessHandler);
  }

  @Autowired
  public void configureGlobal(AuthenticationManagerBuilder auth)
      throws Exception {
    auth.userDetailsService(userDetailsService).passwordEncoder(
        passwordEncoder);
  }
}
