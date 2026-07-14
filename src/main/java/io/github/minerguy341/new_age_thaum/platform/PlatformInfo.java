package io.github.minerguy341.new_age_thaum.platform;

/**
 * Service-layer pattern template (PLAN.md §3 rule 2): common code sees only this interface;
 * each loader entrypoint constructs its own implementation and passes it to the common init.
 * Every future platform gap (data attachments, item handlers, ...) follows this shape.
 */
public interface PlatformInfo {
    String loaderName();

    boolean isDevelopmentEnvironment();
}
