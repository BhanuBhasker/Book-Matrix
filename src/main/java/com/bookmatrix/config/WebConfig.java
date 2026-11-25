package com.bookmatrix.config;

import com.bookmatrix.db.DatabaseReadyInterceptor;
import com.bookmatrix.security.AuthInterceptor;
import com.bookmatrix.security.CsrfInterceptor;
import com.bookmatrix.security.CurrentUserArgumentResolver;
import com.bookmatrix.security.PageAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Wires the request pipeline. For every /api request, in order:
 *   1. CsrfInterceptor          - state-changing requests must carry the CSRF token
 *   2. DatabaseReadyInterceptor - schema exists, otherwise 503
 *   3. AuthInterceptor          - loads the logged-in user, enforces login/admin rules
 * Protected HTML pages are guarded by PageAccessInterceptor (redirects to login).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CsrfInterceptor csrfInterceptor;
    private final DatabaseReadyInterceptor databaseReadyInterceptor;
    private final AuthInterceptor authInterceptor;
    private final PageAccessInterceptor pageAccessInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(CsrfInterceptor csrfInterceptor, DatabaseReadyInterceptor databaseReadyInterceptor,
                     AuthInterceptor authInterceptor, PageAccessInterceptor pageAccessInterceptor,
                     CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.csrfInterceptor = csrfInterceptor;
        this.databaseReadyInterceptor = databaseReadyInterceptor;
        this.authInterceptor = authInterceptor;
        this.pageAccessInterceptor = pageAccessInterceptor;
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(csrfInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(databaseReadyInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(pageAccessInterceptor)
                .addPathPatterns("/admin", "/admin/**", "/bookings.html", "/booking.html", "/profile.html", "/checkout.html");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/admin", "/admin/index.html");
        registry.addRedirectViewController("/admin/", "/admin/index.html");
    }
}
