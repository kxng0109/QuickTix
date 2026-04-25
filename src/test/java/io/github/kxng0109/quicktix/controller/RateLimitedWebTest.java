package io.github.kxng0109.quicktix.controller;

import io.github.kxng0109.quicktix.config.RateLimitTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("slice-test")
@Import(RateLimitTestConfig.class)
public @interface RateLimitedWebTest{
}
