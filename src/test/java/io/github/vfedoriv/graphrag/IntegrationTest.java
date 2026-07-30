package io.github.vfedoriv.graphrag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ActiveProfiles;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(IntegrationTest.TAG)
@ActiveProfiles("test")
@ExtendWith(SharedIntegrationStateExtension.class)
public @interface IntegrationTest {

    String TAG = "integration";
}
